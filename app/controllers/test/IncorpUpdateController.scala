/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.mvc.Action
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository, QueueMongo, QueueRepository}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class IncorpUpdateControllerImpl @Inject()(val injIncorpMongo: IncorpUpdateMongo,
                                           val injQueueMongo: QueueMongo) extends IncorpUpdateController {
  override val repo = injIncorpMongo.repo
  override val queueRepository = injQueueMongo.repo
}

trait IncorpUpdateController extends BaseController {

  val repo: IncorpUpdateRepository
  val queueRepository: QueueRepository

  def add(txId:String, date:Option[String], crn:Option[String] = None, success:Boolean = false) = Action.async {
    implicit request => {
      val status = if(success) "accepted" else "rejected"
      val incorpDate = date map { ISODateTimeFormat.dateParser().withOffsetParsed().parseDateTime(_) }

      val incorpUpdate = IncorpUpdate(txId, status, crn, incorpDate, "-1")

      for {
        _ <- repo.storeIncorpUpdates(Seq(incorpUpdate))
        _ <- queueRepository.storeIncorpUpdates(Seq(QueuedIncorpUpdate(DateTime.now(), IncorpUpdate(txId, status, crn, incorpDate, "-1"))))
      } yield true

      Future.successful(Ok)
    }
  }
}
