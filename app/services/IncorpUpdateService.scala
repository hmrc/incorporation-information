/*
 * Copyright 2023 HM Revenue & Customs
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
import jobs._
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.mongodb.scala.result.UpdateResult
import repositories._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.LockService
import utils.{AlertLogging, DateCalculators, Logging, PagerDutyKeys}

import javax.inject.{Inject, Provider}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try


class IncorpUpdateServiceImpl @Inject()(injConnector: IncorporationAPIConnector,
                                        injIncorpRepo: IncorpUpdateMongo,
                                        injTimepointRepo: TimepointMongo,
                                        injQueueRepo: QueueMongo,
                                        injSubscriptionService: Provider[SubscriptionService],
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

  lazy val lockKeeper: LockService = LockService(lockRepositoryProvider.repo, "incorp-update-job-lock", lockoutTimeout.seconds)
}

trait IncorpUpdateService extends ScheduledService[Either[InsertResult, LockResponse]] with AlertLogging with Logging with DateCalculators {

  val incorporationCheckAPIConnector: IncorporationAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository
  val queueRepository: QueueRepository
  val subscriptionService: SubscriptionService
  val loggingDays: String
  val loggingTimes: String
  val dateCalculators: DateCalculators
  val lockKeeper: LockService

  private[services] def fetchIncorpUpdates(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[IncorpUpdate]] = {
    for {
      timepoint <- timepointRepository.retrieveTimePoint
      incorpUpdates <- incorporationCheckAPIConnector.checkForIncorpUpdate(timepoint)
    } yield {
      incorpUpdates
    }
  }

  private[services] def fetchSpecificIncorpUpdates(timepoint: Option[String]
                                                  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[IncorpUpdate] = {
    for {
      incorpUpdates <- incorporationCheckAPIConnector.checkForIndividualIncorpUpdate(timepoint)
    } yield {
      incorpUpdates.head
    }
  }

  private[services] def storeIncorpUpdates(updates: Seq[IncorpUpdate])
                                          (implicit ec: ExecutionContext): Future[InsertResult] = {
    for {
      result <- incorpUpdateRepository.storeIncorpUpdates(updates)
      alerts <- alertOnNoCTInterest(result.insertedItems)
    } yield {
      result.copy(alerts = alerts)
    }
  }

  private[services] def storeSpecificIncorpUpdate(iUpdate: IncorpUpdate)
                                                 (implicit ec: ExecutionContext): Future[UpdateResult] = {
    for {
      result <- incorpUpdateRepository.storeSingleIncorpUpdate(iUpdate)
    } yield {
      result
    }
  }

  private[services] def alertOnNoCTInterest(updates: Seq[IncorpUpdate])(implicit ec: ExecutionContext): Future[Int] = {

    def logNoSubscription(transactionId: String) {
      logger.error(s"NO_CT_REG_OF_INTEREST for txid $transactionId")
      if (inWorkingHours) {
        logger.error("NO_CT_REG_OF_INTEREST")
      }
    }

    Future.sequence {
      updates.map { iu =>
        for {
          ctSub <- subscriptionService.getSubscription(iu.transactionId, "ct", "scrs")
          ctaxSub <- ctSub.fold(subscriptionService.getSubscription(iu.transactionId, "ctax", "scrs"))(_ => Future(ctSub))
          count = ctaxSub.fold {
            logNoSubscription(iu.transactionId); 1
          }(_ => 0)
        } yield count
      }
    } map {
      _.sum
    }
  }

  private[services] def timepointValidator(timePoint: String): Boolean = {
    Try(dateCalculators.dateGreaterThanNow(timePoint)).getOrElse {
      logger.info(s"[timepointValidator] couldn't parse $timePoint")
      true
    }
  }


  private[services] def latestTimepoint(items: Seq[IncorpUpdate]): String = {

    val log = (badTimePoint: Boolean) => (timepoint: String) =>
      if (badTimePoint) {
        logger.error(s"${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: $timepoint")
      } else {
        ()
      }
    val tp = items.reverse.head.timepoint
    val shouldLog = timepointValidator(tp)
    log(shouldLog)(tp)
    tp
  }

  def invoke(implicit ec: ExecutionContext): Future[Either[InsertResult, LockResponse]] = {
    implicit val hc = HeaderCarrier()
    lockKeeper.withLock(updateNextIncorpUpdateJobLot).map {
      case Some(res) =>
        logger.info("[invoke] IncorpUpdateService acquired lock and returned results")
        logger.info(s"[invoke] Result: $res")
        Left(res)
      case None =>
        logger.info("[invoke] IncorpUpdateService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e: Exception => logger.error(s"[invoke] Error running updateNextIncorpUpdateJobLot with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }

  def updateNextIncorpUpdateJobLot(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[InsertResult] =
    for {
      items <- fetchIncorpUpdates
      ir <- storeIncorpUpdates(items)
      result <- handleInsertResult(items, ir)
    } yield result

  private def copy2queue(insertResult: InsertResult, items: Seq[IncorpUpdate])(implicit ec: ExecutionContext): Future[InsertResult] =
    copyToQueue(createQueuedIncorpUpdates(insertResult.insertedItems)).flatMap {
      case true =>
        timepointRepository.updateTimepoint(latestTimepoint(items)).map(tp => {
          logger.info(s"[updateNextIncorpUpdateJobLot] ${insertResult.inserted} incorp updates were inserted, ${insertResult.duplicate} incorp updates were duplicates, ${insertResult.alerts} alerts and the timepoint has been updated to $tp")
          insertResult
        })
      case false =>
        logger.warn(s"[updateNextIncorpUpdateJobLot] There was an error when copying incorp updates to the new queue")
        Future.successful(insertResult)
    }

  private def handleInsertResult(items: Seq[IncorpUpdate], insertResult: InsertResult)(implicit ec: ExecutionContext): Future[InsertResult] = insertResult match {
    case ir@InsertResult(0, 0, Seq(), 0, _) =>
      logger.info("[updateNextIncorpUpdateJobLot] No Incorp updates were fetched")
      Future.successful(ir)
    case ir@InsertResult(0, d, Seq(), a, _) =>
      logger.info(s"All $d fetched updates were duplicates")
      timepointRepository.updateTimepoint(latestTimepoint(items)).map(tp => {
        logger.info(s"[updateNextIncorpUpdateJobLot] 0 incorp updates were inserted, $d incorp updates were duplicates, $a alerts and the timepoint has been updated to $tp")
        ir
      })
    case ir@InsertResult(_, _, Seq(), _, _) =>
      copy2queue(ir, items)
    case ir@InsertResult(_, _, e, _, _) =>
      logger.warn(s"[updateNextIncorpUpdateJobLot] There was an error when inserting incorp updates, message: ${e.map(_.getMessage)}")
      Future.successful(ir)
  }

  def updateSpecificIncorpUpdateByTP(tps: Seq[String],
                                     forNoQueue: Boolean = false
                                    )(implicit hc: HeaderCarrier,
                                      ec: ExecutionContext): Future[Seq[Boolean]] = {

    def processAllTPs(tp: String) = {
      for {
        iu <- processByTP(tp)
        qe <- getQueueEntry(iu)
        utq <- updateTheQueue(qe, iu)
      }
        yield {
          utq
        }
    }

    def processByTP(tp: String): Future[IncorpUpdate] = {
      logger.info(s"[processByTP] Passed in timepoint is " + tp)
      for {
        item <- fetchSpecificIncorpUpdates(Some(tp))
        result <- storeSpecificIncorpUpdate(item)
      } yield {
        logger.info(s"[processByTP] Result of updating Incorp Update: for " + item + " is: Store result: " + result.wasAcknowledged())
        item
      }
    }

    def getQueueEntry(iu: IncorpUpdate): Future[Option[QueuedIncorpUpdate]] = {
      for {
        q <- queueRepository.getIncorpUpdate(iu.transactionId)
      } yield {
        q
      }
    }

    def updateTheQueue(qEntry: Option[QueuedIncorpUpdate], iUpdate: IncorpUpdate): Future[Boolean] = {
      logger.info(s"[updateTheQueue] About to update queue for txid ${iUpdate.transactionId}")
      qEntry match {
        case Some(qeData) =>
          val txID = qeData.incorpUpdate.transactionId
          for {
            removeQueue <- queueRepository.removeQueuedIncorpUpdate(txID)
            copyTOQ <- copyToQueue(createQueuedIncorpUpdates(Seq(iUpdate)))
          }
            yield {
              logger.info(s"[updateTheQueue] Results of updating the queue for txid: " + qeData.incorpUpdate.transactionId + " Remove from queue result: " + removeQueue + " Copy to queue result: " + copyTOQ)
              true
            }
        case _ => if (forNoQueue) {
          copyToQueue(createQueuedIncorpUpdates(Seq(iUpdate))).map { copyToQ =>
            logger.info(s"[updateTheQueue] Result of adding a new queue entry: " + copyToQ)
            true
          }
        }
        else {
          logger.info(s"[updateTheQueue] No queue update done")
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
    val dateTime = delayInMinutes.fold(getDateTimeNowUTC)(delay => getDateTimeNowUTC.plusMinutes(delay))
    QueuedIncorpUpdate(dateTime, incorpUpdate)
  }

  def copyToQueue(queuedIncorpUpdates: Seq[QueuedIncorpUpdate])
                 (implicit ec: ExecutionContext): Future[Boolean] = {
    queueRepository.storeIncorpUpdates(queuedIncorpUpdates).map { r =>
      logger.info(s"[copyToQueue] Incorp updates to be copied to queue = $queuedIncorpUpdates\nresult = $r")
      r.inserted == queuedIncorpUpdates.length
    }
  }

  def upsertToQueue(queuedUpdate: QueuedIncorpUpdate)
                   (implicit ec: ExecutionContext): Future[Boolean] = {
    queueRepository.upsertIncorpUpdate(queuedUpdate) map { res =>
      logger.info(s"[upsertToQueue] - upsert result for transaction id : ${queuedUpdate.incorpUpdate.transactionId} - ${res.toString}")
      res.errors.isEmpty
    }
  }
}

