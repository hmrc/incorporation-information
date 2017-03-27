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

import play.api.mvc.Action
import repositories.SuccessfulSub
import services.SubscriptionService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global


class SubscriptionControllerImpl extends SubscriptionController {
  override protected val service = SubscriptionService
}

trait SubscriptionController extends BaseController {

  protected val service: SubscriptionService

  def setupSubscription(transactionId: String, regime: String, subscriber: String) = Action.async {
    implicit request =>
      service.addSubscription(transactionId, regime, subscriber).map {
        case SuccessfulSub => Accepted("You have successfully added a subscription")
        case _ => InternalServerError
      }
  }
}
