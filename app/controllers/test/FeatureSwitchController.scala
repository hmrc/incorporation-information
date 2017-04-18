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

import play.api.Logger
import play.api.mvc.Action
import uk.gov.hmrc.play.microservice.controller.BaseController
import utils.{SCRSFeatureSwitches, FeatureSwitch}

import scala.concurrent.Future

class FeatureSwitchControllerImpl @Inject()() extends FeatureSwitchController

trait FeatureSwitchController extends BaseController {

  val fs = FeatureSwitch

  def switch(featureName: String, state: String) = Action.async {
    implicit request =>

      lazy val feature = state.toLowerCase match {
        case "stub" | "off" => fs.disable(FeatureSwitch(featureName, enabled = false))
        case "coho" | "on" => fs.enable(FeatureSwitch(featureName, enabled = true))
        case _ => throw new Exception(s"[FeatureSwitchController] $state was not a valid state for $featureName - try coho / on to enable or stub / off to disable")
      }

      SCRSFeatureSwitches(featureName) match {
        case Some(_) =>
          val message = s"[FeatureSwitch] Feature switch for ${feature.name} is now ${if(feature.enabled) "enabled" else "disabled"}"
          Logger.info(message)
          Future.successful(Ok(message))
        case None =>
          Logger.info(s"[FeatureSwitch] No feature switch with the name $featureName could not be found")
          Future.successful(BadRequest)
      }
  }
}
