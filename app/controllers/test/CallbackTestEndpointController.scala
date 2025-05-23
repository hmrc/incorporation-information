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

package controllers.test


import utils.Logging
import play.api.libs.json.JsValue
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.{BackendBaseController, BackendController}

import javax.inject.Inject
import scala.concurrent.Future


class CallbackTestEndpointControllerImpl @Inject()(override val controllerComponents: ControllerComponents
                                                  ) extends BackendController(controllerComponents) with CallbackTestEndpointController

trait CallbackTestEndpointController extends BackendBaseController with Logging {

  def post: Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      logger.info("[Callback] - callback to test endpoint received")
      Future.successful(Ok)
  }
}
