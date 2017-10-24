/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalTime
import javax.inject.{Inject, Provider, Singleton}

import config.MicroserviceConfig
import connectors.IncorporationAPIConnector
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import play.api.Logger
import repositories._
import repositories.{IncorpUpdateRepository, InsertResult, TimepointRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import org.joda.time.DateTime
import reactivemongo.api.commands.UpdateWriteResult
import utils.DateCalculators

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class IncorpUpdateServiceImpl @Inject()(injConnector: IncorporationAPIConnector,
                                        injIncorpRepo: IncorpUpdateMongo,
                                        injTimepointRepo: TimepointMongo,
                                        injQueueRepo: QueueMongo,
                                        injSubscriptionService : Provider[SubscriptionService],
                                        config: MicroserviceConfig
                                       ) extends IncorpUpdateService {
  override val incorporationCheckAPIConnector = injConnector
  override val incorpUpdateRepository = injIncorpRepo.repo
  override val timepointRepository = injTimepointRepo.repo
  override val queueRepository = injQueueRepo.repo
  override val subscriptionService = injSubscriptionService.get()
  override val noRAILoggingDay = config.noRegisterAnInterestLoggingDay
  override val noRAILoggingTime = config.noRegisterAnInterestLoggingTime

}

trait IncorpUpdateService extends {

  val incorporationCheckAPIConnector: IncorporationAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository
  val queueRepository: QueueRepository
  val subscriptionService: SubscriptionService
  val noRAILoggingDay: String
  val noRAILoggingTime: String

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
    Future.sequence {
      updates.map(iu =>
        subscriptionService.getSubscription(iu.transactionId, "ct", "scrs").map {
          _.fold {
            Logger.error(s"NO_CT_REG_OF_INTEREST for txid ${iu.transactionId}")
            if (inWorkingHours) {
              Logger.error("NO_CT_REG_OF_INTEREST")
            }
            1
          } { _ => 0 }
        })
    } map { _.sum }
  }

  private[services] def latestTimepoint(items: Seq[IncorpUpdate]): String = items.reverse.head.timepoint

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

  def updateSpecificIncorpUpdateByTP(tps: Seq[String])(implicit hc: HeaderCarrier): Future[Seq[UpdateWriteResult]] = {
    def processByTP(tp: String) = {
      Logger.info(s"Updating timepoint " + tp)
      for {
        item <- fetchSpecificIncorpUpdates(Some(tp))
        result <- storeSpecificIncorpUpdate(item)
      } yield {
        Logger.info(s"Result of updating Incorp Update:" + result)
        result
      }
    }

    Future.sequence(tps.map(processByTP))
  }

  def inWorkingHours: Boolean = {
    DateCalculators.loggingDay(noRAILoggingDay, DateCalculators.getTheDay(DateTime.now)) &&
      DateCalculators.loggingTime(noRAILoggingTime, LocalTime.now)
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

