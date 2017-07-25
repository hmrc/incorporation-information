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

import javax.inject.{Inject, Singleton}

import connectors.IncorporationAPIConnector
import models.{IncorpUpdate, QueuedIncorpUpdate}
import play.api.Logger
import repositories._
import repositories.{IncorpUpdateRepository, InsertResult, TimepointRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class IncorpUpdateServiceImpl @Inject()(injConnector: IncorporationAPIConnector,
                                        injIncorpRepo: IncorpUpdateMongo,
                                        injTimepointRepo: TimepointMongo,
                                        injQueueRepo: QueueMongo
                                       ) extends IncorpUpdateService {
  override val incorporationCheckAPIConnector = injConnector
  override val incorpUpdateRepository = injIncorpRepo.repo
  override val timepointRepository = injTimepointRepo.repo
  override val queueRepository = injQueueRepo.repo
}

trait IncorpUpdateService {

  val incorporationCheckAPIConnector: IncorporationAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository
  val queueRepository: QueueRepository


  private[services] def fetchIncorpUpdates(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    for {
      timepoint <- timepointRepository.retrieveTimePoint
      incorpUpdates <- incorporationCheckAPIConnector.checkForIncorpUpdate(timepoint)(hc)
    } yield {
      incorpUpdates
    }
  }

  private[services] def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    for {
      result <- incorpUpdateRepository.storeIncorpUpdates(updates)

    } yield {
      result
    }
  }

  private[services] def latestTimepoint(items: Seq[IncorpUpdate]): String = items.reverse.head.timepoint

  // TODO - look to refactor this into a simpler for-comprehension
  def updateNextIncorpUpdateJobLot(implicit hc: HeaderCarrier): Future[InsertResult] = {
    fetchIncorpUpdates flatMap { items =>
      storeIncorpUpdates(items) flatMap {
        case ir@InsertResult(0, _, Seq()) => {
          Logger.info("No Incorp updates were fetched")
          Future.successful(ir)
        }
        case ir@InsertResult(i, d, Seq()) => {
          copyToQueue(createQueuedIncorpUpdates(items)) flatMap {
            case true =>
              timepointRepository.updateTimepoint(latestTimepoint(items)).map(tp => {
                val message = s"$i incorp updates were inserted, $d incorp updates were duplicates, and the timepoint has been updated to $tp"
                Logger.info(message)
                ir
              })
            case false =>
              Logger.info(s"There was an error when copying incorp updates to the new queue")
              Future.successful(ir)
          }
        }
        case ir@InsertResult(_, _, e) =>
          Logger.info(s"There was an error when inserting incorp updates, message: $e")
          Future.successful(ir)
      }
    }
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

