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

import constants.JobNames._
import javax.inject.{Inject, Named}
import jobs.ScheduledJob
import play.api.Logger
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.controller.{BackendBaseController, BackendController}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ManualTriggerControllerImpl @Inject()(@Named("incorp-update-job") val incUpdatesJob: ScheduledJob,
                                            @Named("fire-subs-job") val fireSubJob: ScheduledJob,
                                            override val controllerComponents: ControllerComponents)
  extends BackendController(controllerComponents) with ManualTriggerController

trait ManualTriggerController extends BackendBaseController {

  protected val incUpdatesJob: ScheduledJob
  protected val fireSubJob: ScheduledJob

  def triggerJob(jobName: String): Action[AnyContent] = Action.async {
    implicit request =>
      jobName match {
        case INCORP_UPDATE => triggerIncorpUpdateJob
        case FIRE_SUBS => triggerFireSubsJob
        case _ =>
          val message = s"$jobName did not match any known jobs"
          Logger.info(message)
          Future.successful(NotFound(message))
      }
  }

  private def triggerIncorpUpdateJob: Future[Result] = incUpdatesJob
    .scheduledMessage
    .service
    .invoke map (res => Ok(res.toString))

  private def triggerFireSubsJob: Future[Result] = fireSubJob
    .scheduledMessage
    .service
    .invoke map (res => Ok(res.toString))
}