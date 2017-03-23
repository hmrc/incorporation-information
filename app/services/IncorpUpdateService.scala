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

import connectors.IncorporationCheckAPIConnector
import models.IncorpUpdate
import mongo.Repositories
import play.api.Logger
import reactivemongo.api.commands.WriteError
import repositories.{IncorpUpdateRepository, InsertResult, TimepointRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by jackie on 22/03/17.
  */
object IncorpUpdateService extends IncorpUpdateService {

  override val incorporationCheckAPIConnector = IncorporationCheckAPIConnector
  override val incorpUpdateRepository = Repositories.incorpUpdateRepository
  override val timepointRepository = Repositories.timepointRepository

}


trait IncorpUpdateService {

  val incorporationCheckAPIConnector: IncorporationCheckAPIConnector
  val incorpUpdateRepository: IncorpUpdateRepository
  val timepointRepository: TimepointRepository


  private[services] def fetchIncorpUpdates(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    for {
      timepoint <- timepointRepository.retrieveTimePoint
      submission <- incorporationCheckAPIConnector.checkSubmission(timepoint)(hc)
    } yield {
      submission.items
    }
  }

  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    for {
      result <- incorpUpdateRepository.storeIncorpUpdates(updates)
    } yield {
      result
    }
  }

  private[services] def latestTimepoint(items: Seq[IncorpUpdate]): String = items.reverse.head.timepoint

  //the schedule job calls this function???
    def updateNextIncorpUpdateJobLot(implicit hc: HeaderCarrier): Future[Boolean] = {
      fetchIncorpUpdates flatMap { items =>
        storeIncorpUpdates(items) flatMap {
          case InsertResult(0, _, Seq()) => {
            Logger.info("No Incorp updates were fetched")
            Future(false)
          }
          case InsertResult(i, d, Seq()) => {
            timepointRepository.updateTimepoint(latestTimepoint(items))
              .map(tp => {
                s"Incorporation ${items.reverse.head.status} - Timepoint updated to $tp"
                Logger.info(s"$i incorp updates were inserted, $d incorp updates were duplicates, and the timepoint has been updated to $tp")
              })
            Future(true)
          }
          case InsertResult(_, _, e) => {
            Logger.info(s"There was an error when inserting incorp updates, message: $e")
            Future(false)
          }
        }
      }
    }


}

