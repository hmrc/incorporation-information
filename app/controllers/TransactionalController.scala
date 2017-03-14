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

package controllers

import javax.inject.Inject

import com.google.inject.ImplementedBy
import connectors.{FailedTransactionalResponse, SuccessfulTransactionalResponse}
import play.api.mvc.Action
import play.api.mvc.Results.{Ok, NotFound}
import services.TransactionalService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

class TransactionalControllerImpl @Inject()(val service: TransactionalService) extends TransactionalController

@ImplementedBy(classOf[TransactionalControllerImpl])
trait TransactionalController extends BaseController {

  val service: TransactionalService

  def fetchCompanyProfile(transactionId: String) = Action.async {
    implicit request =>
      service.fetchCompanyProfile(transactionId).map {
        case SuccessfulTransactionalResponse(json) =>
          println("============== pucker")
          Ok(json)
        case FailedTransactionalResponse => NotFound
      }
  }
}
