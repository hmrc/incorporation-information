/*
 * Copyright 2022 HM Revenue & Customs
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
import jobs.ScheduledJob
import play.api.Logging
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.{BackendBaseController, BackendController}

import javax.inject.{Inject, Named}
import scala.concurrent.{ExecutionContext, Future}

class ManualTriggerControllerImpl @Inject()(@Named("incorp-update-job") val incUpdatesJob: ScheduledJob,
                                            @Named("fire-subs-job") val fireSubJob: ScheduledJob,
                                            override val controllerComponents: ControllerComponents
                                           )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with ManualTriggerController

trait ManualTriggerController extends BackendBaseController with Logging {

  implicit val ec: ExecutionContext
  protected val incUpdatesJob: ScheduledJob
  protected val fireSubJob: ScheduledJob

  def triggerJob(jobName: String): Action[AnyContent] = Action.async {
    jobName match {
      case INCORP_UPDATE => triggerIncorpUpdateJob
      case FIRE_SUBS => triggerFireSubsJob
      case _ =>
        val message = s"$jobName did not match any known jobs"
        logger.info(message)
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