/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.test

import javax.inject.Inject
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository, QueueMongo, QueueRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.{BackendBaseController, BackendController}

import scala.concurrent.{ExecutionContext, Future}


class IncorpUpdateControllerImpl @Inject()(val injIncorpMongo: IncorpUpdateMongo,
                                           val injQueueMongo: QueueMongo,
                                           override val controllerComponents: ControllerComponents
                                          )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with IncorpUpdateController {
  override lazy val repo = injIncorpMongo.repo
  override lazy val queueRepository = injQueueMongo.repo
}

trait IncorpUpdateController extends BackendBaseController {

  implicit val ec: ExecutionContext
  val repo: IncorpUpdateRepository
  val queueRepository: QueueRepository

  def add(txId: String,
          date: Option[String],
          crn: Option[String] = None,
          success: Boolean = false
         ): Action[AnyContent] = Action.async {
    val status = if (success) "accepted" else "rejected"
    val incorpDate = date map {
      ISODateTimeFormat.dateParser().withOffsetParsed().parseDateTime(_)
    }

    val incorpUpdate = IncorpUpdate(txId, status, crn, incorpDate, "-1")

    for {
      _ <- repo.storeIncorpUpdates(Seq(incorpUpdate))
      _ <- queueRepository.storeIncorpUpdates(Seq(QueuedIncorpUpdate(DateTime.now(), IncorpUpdate(txId, status, crn, incorpDate, "-1"))))
    } yield Ok
  }
}