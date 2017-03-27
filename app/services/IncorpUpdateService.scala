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

import connectors.IncorporationCheckAPIConnector
import models.IncorpUpdate
import play.api.Logger
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class IncorpUpdateServiceImpl @Inject()(
                                         injConnector: IncorporationCheckAPIConnector,
                                         injIncorpRepo: IncorpUpdateMongo,
                                         injTimepointRepo: TimepointMongo
                                       ) extends IncorpUpdateService {
  override val incorporationCheckAPIConnector = injConnector
  override val incorpUpdateRepository = injIncorpRepo.repo
  override val timepointRepository = injTimepointRepo.repo
}

trait IncorpUpdateService {

  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository


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

  def updateNextIncorpUpdateJobLot(implicit hc: HeaderCarrier): Future[InsertResult] = {

    fetchIncorpUpdates flatMap { items =>
      storeIncorpUpdates(items) map { ir =>
        ir match {
          case InsertResult(0, _, Seq()) => Logger.info("No Incorp updates were fetched")
          case InsertResult(i, d, Seq()) => {
            timepointRepository.updateTimepoint(latestTimepoint(items)).map(tp => {
              val message = s"$i incorp updates were inserted, $d incorp updates were duplicates, and the timepoint has been updated to $tp"
              Logger.info(message)
            }
            )
          }
          case InsertResult(_, _, e) => Logger.info(s"There was an error when inserting incorp updates, message: $e")
        }
        ir
      }
    }
  }


}

