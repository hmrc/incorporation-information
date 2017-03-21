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

package jobs


import mongo.Repositories
import org.joda.time.Duration
import play.api.Logger
import services.RegistrationHoldingPenService
import uk.gov.hmrc.lock.LockKeeper
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}

// TODO - II-INCORP - needs a rename though
trait CheckSubmissionJob extends ExclusiveScheduledJob with JobConfig {

  val lock: LockKeeper
  val regHoldingPenService: RegistrationHoldingPenService

  //$COVERAGE-OFF$
  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    SCRSFeatureSwitches.scheduler.enabled match {
      case true => lock.tryLock {
        Logger.info(s"Triggered $name")
        // TODO - II-INCORP - not holding pen service now - method name needs altering
//        regHoldingPenService.updateNextSubmissionByTimepoint(HeaderCarrier())
        Future.successful(Result(s"Feature is turned on"))
      } map {
        case Some(x) =>
          Logger.info(s"successfully acquired lock for $name")
          Result(s"$name")
        case None =>
          Logger.info(s"failed to acquire lock for $name")
          Result(s"$name failed")
      } recover {
        case _: Exception => Result(s"$name failed")
      }
      case false => Future.successful(Result(s"Feature is turned off"))
    }
  }
  //$COVERAGE-ON$
}

object CheckSubmissionJob extends CheckSubmissionJob {
  val name = "check-submission-job"
  lazy val regHoldingPenService = RegistrationHoldingPenService

  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    override val repo = Repositories.lockRepository
  }
}

