/*
 * Copyright 2021 HM Revenue & Customs
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

import javax.inject.{Inject, Named}
import jobs.ScheduledJob
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.{BackendBaseController, BackendController}
import utils.FeatureSwitch

import scala.concurrent.Future

class FeatureSwitchControllerImpl @Inject()(
                                             @Named("incorp-update-job") val incUpdatesJob: ScheduledJob,
                                             @Named("metrics-job") val metricsJob: ScheduledJob,
                                             @Named("fire-subs-job") val fireSubsJob: ScheduledJob,
                                             @Named("proactive-monitoring-job") val proJob: ScheduledJob,
                                             override val controllerComponents: ControllerComponents
                                           ) extends BackendController(controllerComponents) with FeatureSwitchController

trait FeatureSwitchController extends BackendBaseController {
  val incUpdatesJob: ScheduledJob
  val metricsJob: ScheduledJob
  val fireSubsJob: ScheduledJob
  val proJob: ScheduledJob

  val fs = FeatureSwitch

  def switch(featureName: String, state: String): Action[AnyContent] = Action.async {
    implicit request =>

      val truthy: Boolean = Seq("coho", "on", "true", "enable").contains(state)

      val feature = (featureName, truthy) match {
        case ("metrics-job", true) => metricsJob.scheduler.resumeJob("metrics-job")
        case ("metrics-job", false) => metricsJob.scheduler.suspendJob("metrics-job")
        case ("fire-subs-job", true) => fireSubsJob.scheduler.resumeJob("fire-subs-job")
        case ("fire-subs-job", false) => fireSubsJob.scheduler.suspendJob("fire-subs-job")
        case ("incorp-update-job", true) => fireSubsJob.scheduler.resumeJob("incorp-update-job")
        case ("incorp-update-job", false) => fireSubsJob.scheduler.suspendJob("incorp-update-job")
        case ("proactive-monitoring-job", true) => fireSubsJob.scheduler.resumeJob("proactive-monitoring-job")
        case ("proactive-monitoring-job", false) => fireSubsJob.scheduler.suspendJob("proactive-monitoring-job")
        case (_, true) => fs.enable(FeatureSwitch(featureName, enabled = true))
        case _ => fs.disable(FeatureSwitch(featureName, enabled = false))

      }
      Future.successful(Ok(s"[FeatureSwitch] Feature switch for $featureName is now $truthy"))
  }
}
