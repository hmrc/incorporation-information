/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.Future


class CallbackTestEndpointControllerImpl @Inject()() extends CallbackTestEndpointController

trait CallbackTestEndpointController extends BaseController {
  val post = Action.async(parse.json) {
    implicit request =>
      withJsonBody[JsObject] { js =>
        Logger.info("[Callback] - callback to test endpoint received")
        Future.successful(Ok)
      }
  }
}
