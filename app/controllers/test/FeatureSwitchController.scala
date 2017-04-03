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

import javax.inject.Inject

import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import utils.{SCRSFeatureSwitches, FeatureSwitch}

import scala.concurrent.Future

class FeatureSwitchControllerImpl @Inject()() extends FeatureSwitchController

trait FeatureSwitchController extends BaseController {

  val fs = FeatureSwitch

  def switch(featureName: String, state: String) = Action.async {
    implicit request =>

      def feature = state match {
        case "stub" => fs.disable(FeatureSwitch(featureName, enabled = false))
        case "coho" => fs.enable(FeatureSwitch(featureName, enabled = true))
        case _ => fs.disable(FeatureSwitch(featureName, enabled = false))
      }

      SCRSFeatureSwitches(featureName) match {
        case Some(_) => Future.successful(Ok(feature.toString))
        case None => Future.successful(BadRequest)
      }
  }
}
