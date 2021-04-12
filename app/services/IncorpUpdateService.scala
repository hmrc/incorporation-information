/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import config.MicroserviceConfig
import connectors.IncorporationAPIConnector
import javax.inject.{Inject, Provider}
import jobs._
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.{DateTime, Duration}
import play.api.Logger
import reactivemongo.api.commands.UpdateWriteResult
import repositories.{IncorpUpdateRepository, InsertResult, TimepointRepository, _}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockKeeper
import utils.{AlertLogging, DateCalculators, PagerDutyKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class IncorpUpdateServiceImpl @Inject()(injConnector: IncorporationAPIConnector,
                                        injIncorpRepo: IncorpUpdateMongo,
                                        injTimepointRepo: TimepointMongo,
                                        injQueueRepo: QueueMongo,
                                        injSubscriptionService : Provider[SubscriptionService],
                                        msConfig: MicroserviceConfig,
                                        val dateCalculators: DateCalculators,
                                        lockRepositoryProvider: LockRepositoryProvider
                                       ) extends IncorpUpdateService {
  lazy val incorporationCheckAPIConnector = injConnector
  lazy val incorpUpdateRepository = injIncorpRepo.repo
  lazy val timepointRepository = injTimepointRepo.repo
  lazy val queueRepository = injQueueRepo.repo
  lazy val subscriptionService = injSubscriptionService.get()
  lazy val loggingDays = msConfig.noRegisterAnInterestLoggingDay
  lazy val loggingTimes = msConfig.noRegisterAnInterestLoggingTime

  lazy val lockoutTimeout = msConfig.getConfigInt("schedules.incorp-update-job.lockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = "incorp-update-job-lock"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(lockoutTimeout)
    override lazy val repo = lockRepositoryProvider.repo
  }
}

trait IncorpUpdateService extends ScheduledService[Either[InsertResult, LockResponse]] with AlertLogging {

  val incorporationCheckAPIConnector: IncorporationAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository
  val queueRepository: QueueRepository
  val subscriptionService: SubscriptionService
  val loggingDays: String
  val loggingTimes: String
  val dateCalculators: DateCalculators
  val lockKeeper: LockKeeper

  private[services] def fetchIncorpUpdates(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    for {
      timepoint <- timepointRepository.retrieveTimePoint
      incorpUpdates <- incorporationCheckAPIConnector.checkForIncorpUpdate(timepoint)(hc)
    } yield {
      incorpUpdates
    }
  }

  private[services] def fetchSpecificIncorpUpdates(timepoint: Option[String])(implicit hc: HeaderCarrier): Future[IncorpUpdate] = {
    for {
      incorpUpdates <- incorporationCheckAPIConnector.checkForIndividualIncorpUpdate(timepoint)(hc)
    } yield {
      incorpUpdates.head
    }
  }

  private[services] def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    for {
      result <- incorpUpdateRepository.storeIncorpUpdates(updates)
      alerts <- alertOnNoCTInterest(result.insertedItems)
    } yield {
      result.copy(alerts=alerts)
    }
  }

  private[services] def storeSpecificIncorpUpdate(iUpdate: IncorpUpdate): Future[UpdateWriteResult] = {
    for {
      result <- incorpUpdateRepository.storeSingleIncorpUpdate(iUpdate)
      } yield {
      result
    }
  }

  private[services] def alertOnNoCTInterest(updates: Seq[IncorpUpdate]): Future[Int] = {

    // TODO SCRS-11013 got pager duty outside of working hours - alert log scanner matching substring?
    def logNoSubscription(transactionId: String) {
      Logger.error(s"NO_CT_REG_OF_INTEREST for txid $transactionId")
      if (inWorkingHours) { Logger.error("NO_CT_REG_OF_INTEREST") }
    }

    Future.sequence {
      updates.map { iu =>
        for {
          ctSub   <- subscriptionService.getSubscription(iu.transactionId, "ct", "scrs")
          ctaxSub <- ctSub.fold(subscriptionService.getSubscription(iu.transactionId, "ctax", "scrs"))(_ => Future(ctSub))
          count    = ctaxSub.fold{ logNoSubscription(iu.transactionId); 1 }(_ => 0)
        } yield count
      }
    } map { _.sum }
  }

  private[services] def timepointValidator(timePoint:String): Boolean = {
    Try(dateCalculators.dateGreaterThanNow(timePoint)).getOrElse {
      Logger.info(s"couldn't parse $timePoint")
      true }
  }


  private[services] def latestTimepoint(items: Seq[IncorpUpdate]): String = {

    val log = (badTimePoint: Boolean) => (timepoint:String) =>
      if(badTimePoint) {
        Logger.error(s"${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: $timepoint")
      } else {()}
    val tp = items.reverse.head.timepoint
    val shouldLog = timepointValidator(tp)
    log(shouldLog)(tp)
    tp
  }
def invoke(implicit ec: ExecutionContext):Future[Either[InsertResult,LockResponse]] = {
  implicit val hc = HeaderCarrier()
    lockKeeper.tryLock(updateNextIncorpUpdateJobLot).map {
      case Some(res) =>
        Logger.info("IncorpUpdateService acquired lock and returned results")
        Logger.info(s"Result: $res")
        Left(res)
      case None =>
        Logger.info("IncorpUpdateService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e: Exception => Logger.error(s"Error running updateNextIncorpUpdateJobLot with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }

  // TODO - look to refactor this into a simpler for-comprehension
  def updateNextIncorpUpdateJobLot(implicit hc: HeaderCarrier): Future[InsertResult] = {
    fetchIncorpUpdates flatMap { items =>
      storeIncorpUpdates(items) flatMap {
        case ir@InsertResult(0, 0, Seq(), 0, _) => {
          Logger.info("No Incorp updates were fetched")
          Future.successful(ir)
        }
        case ir@InsertResult(0, d, Seq(), a, _) => {
          Logger.info(s"All $d fetched updates were duplicates")
          timepointRepository.updateTimepoint(latestTimepoint(items)).map(tp => {
            val message = s"0 incorp updates were inserted, $d incorp updates were duplicates, $a alerts and the timepoint has been updated to $tp"
            Logger.info(message)
            ir
          })
        }
        case ir@InsertResult(i, d, Seq(), a, insertedItems) => {
          copyToQueue(createQueuedIncorpUpdates(insertedItems)) flatMap {
            case true =>
              timepointRepository.updateTimepoint(latestTimepoint(items)).map(tp => {
                val message = s"$i incorp updates were inserted, $d incorp updates were duplicates, $a alerts and the timepoint has been updated to $tp"
                Logger.info(message)
                ir
              })
            case false =>
              Logger.info(s"There was an error when copying incorp updates to the new queue")
              Future.successful(ir)
          }
        }
        case ir@InsertResult(_, _, e, _, _) =>
          Logger.info(s"There was an error when inserting incorp updates, message: $e")
          Future.successful(ir)
      }
    }
  }

  def updateSpecificIncorpUpdateByTP(tps: Seq[String],forNoQueue: Boolean = false)(implicit hc: HeaderCarrier): Future[Seq[Boolean]] = {

    def processAllTPs(tp:String) = {
      for {
        iu <- processByTP(tp)
        qe <- getQueueEntry(iu)
        utq <- updateTheQueue(qe,iu)
      }
        yield {
        utq
        }
    }

    def processByTP(tp: String) : Future[IncorpUpdate] = {
      val logPrefix = "[IncorpUpdateService] [processByTP]"
      Logger.info(s"${logPrefix} Passed in timepoint is " + tp)
      for {
        item <- fetchSpecificIncorpUpdates(Some(tp))
        result <- storeSpecificIncorpUpdate(item)
      } yield {
        Logger.info(s"${logPrefix} Result of updating Incorp Update: for "  + item + " is: Store result: "  + result.ok)
        item
        }
      }

    def getQueueEntry(iu: IncorpUpdate ) : Future[Option[QueuedIncorpUpdate]]= {
      for {
        q <- queueRepository.getIncorpUpdate(iu.transactionId)
      } yield {
        q
      }
    }

    def updateTheQueue(qEntry: Option[QueuedIncorpUpdate], iUpdate: IncorpUpdate) : Future[Boolean] = {
      val logPrefix = "[IncorpUpdateService] [updateTheQueue]"
      Logger.info(s"${logPrefix} About to update queue for txid ${iUpdate.transactionId}")
      qEntry match {
        case Some(qeData) =>
          val txID = qeData.incorpUpdate.transactionId
          for {
            removeQueue <- queueRepository.removeQueuedIncorpUpdate(txID)
            copyTOQ <- copyToQueue(createQueuedIncorpUpdates(Seq(iUpdate)))
          }
            yield {
              Logger.info(s"${logPrefix} Results of updating the queue for txid: " + qeData.incorpUpdate.transactionId + " Remove from queue result: " + removeQueue + " Copy to queue result: " + copyTOQ)
              true
            }
        case _ => if (forNoQueue) {
          copyToQueue(createQueuedIncorpUpdates(Seq(iUpdate))).map { copyToQ =>
            Logger.info(s"${logPrefix} Result of adding a new queue entry: " + copyToQ)
            true
          }
        }
        else {
          Logger.info(s"${logPrefix} No queue update done")
          Future.successful(false)
        }
      }
    }

    Future.sequence(tps.map(processAllTPs))
  }

  def createQueuedIncorpUpdates(incorpUpdates: Seq[IncorpUpdate], delayInMinutes: Option[Int] = None): Seq[QueuedIncorpUpdate] = {
    incorpUpdates map (incorp => createQueuedIncorpUpdate(incorp, delayInMinutes))
  }

  def createQueuedIncorpUpdate(incorpUpdate: IncorpUpdate, delayInMinutes: Option[Int] = None): QueuedIncorpUpdate = {
    val dateTime = delayInMinutes.fold(DateTime.now())(delay => DateTime.now().plusMinutes(delay))
    QueuedIncorpUpdate(dateTime, incorpUpdate)
  }

  def copyToQueue(queuedIncorpUpdates: Seq[QueuedIncorpUpdate]): Future[Boolean] = {
    queueRepository.storeIncorpUpdates(queuedIncorpUpdates).map { r =>
      // TODO - explain result
      Logger.info(s"Incorp updates to be copied to queue = $queuedIncorpUpdates")
      Logger.info(s"result = $r")
      r.inserted == queuedIncorpUpdates.length
    }
  }

  def upsertToQueue(queuedUpdate: QueuedIncorpUpdate): Future[Boolean] = {
    queueRepository.upsertIncorpUpdate(queuedUpdate) map { res =>
      Logger.info(s"[IncorpUpdateService] [upsertToQueue] upsert result for transaction id : ${queuedUpdate.incorpUpdate.transactionId} - ${res.toString}")
      res.errors.isEmpty
    }
  }
}

