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

package controllers.test

import javax.inject.{Inject, Named, Singleton}

import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import models.IncorpUpdate
import org.joda.time.format.ISODateTimeFormat
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository}

import scala.concurrent.Future

@Singleton
class IncorpUpdateControllerImpl @Inject()(val injIncorpRepo: IncorpUpdateMongo) extends IncorpUpdateController {
  override val repo = injIncorpRepo.repo
}

trait IncorpUpdateController extends BaseController {

  val repo: IncorpUpdateRepository

  def add(txId:String, date:Option[String], crn:Option[String] = None, success:Boolean = false) = Action.async {
    implicit request => {
      val status = if(success) "accepted" else "rejected"
      val incorpDate = date map { ISODateTimeFormat.dateParser().withOffsetParsed().parseDateTime(_) }
      repo.storeIncorpUpdates(Seq(
        IncorpUpdate(txId, status, crn, incorpDate, "-1")
      ))
          Future.successful(Ok)
      }
  }
}
