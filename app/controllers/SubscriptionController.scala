/*
 * Copyright 2024 HM Revenue & Customs
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


import models.{IncorpUpdate, IncorpUpdateResponse}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories._
import services.SubscriptionService
import uk.gov.hmrc.play.bootstrap.backend.controller.{BackendBaseController, BackendController}

import javax.inject.Inject
import scala.concurrent.ExecutionContext


class SubscriptionControllerImpl @Inject()(val service: SubscriptionService,
                                           override val controllerComponents: ControllerComponents
                                          )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with SubscriptionController

trait SubscriptionController extends BackendBaseController {

  implicit val ec: ExecutionContext
  protected val service: SubscriptionService

  def checkSubscription(transactionId: String,
                        regime: String,
                        subscriber: String,
                        force: Boolean = false
                       ): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[JsObject] { js =>
        val callbackUrl = (js \ "SCRSIncorpSubscription" \ "callbackUrl").as[String]
        service.checkForSubscription(transactionId, regime, subscriber, callbackUrl, forced = force).map {
          case IncorpExists(update) =>
            val response = toResponse(regime, subscriber, callbackUrl, update)
            Ok(Json.toJson(response)(IncorpUpdateResponse.writes))
          case SuccessfulSub(wasForced) => Accepted(Json.obj("forced" -> wasForced))
          case _ => InternalServerError
        }
      }
  }

  def removeSubscription(transactionId: String,
                         regime: String,
                         subscriber: String
                        ): Action[AnyContent] = Action.async {
      service.deleteSubscription(transactionId, regime, subscriber).map {
        case DeletedSub => Ok
        case NotDeletedSub => NotFound
        case _ => InternalServerError
      }
  }

  private[controllers] def toResponse(regime: String, sub: String, url: String, update: IncorpUpdate): IncorpUpdateResponse =
    IncorpUpdateResponse(regime, sub, url, update)

}