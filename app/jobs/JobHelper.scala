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

package jobs

import org.joda.time.Duration
import play.api.Logger
import reactivemongo.api.DefaultDB
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.FeatureSwitch

import scala.concurrent.{ExecutionContext, Future}

trait JobHelper extends JobConfig {
  self: ExclusiveScheduledJob =>

  val db: () => DefaultDB
  lazy val lock: LockKeeper = buildLockKeeper

  type JobResult = self.Result
  def jobResult(message: String): JobResult = Result(message)

  def ifFeatureEnabled(feature: FeatureSwitch)(f: => Future[JobResult]): Future[JobResult] = {
    if(feature.enabled) {
      Logger.info(s"Feature ${feature.name} is turned on")
      f
    } else {
      Logger.info(s"Feature ${feature.name} is turned off")
      Future.successful(jobResult(s"Feature $name is turned off"))
    }
  }

  def buildLockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = s"${self.name}-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo: () => DefaultDB = db
    override val repo = new LockRepository
  }

  def whenLockAcquired[T](f: => Future[T])(implicit ex: ExecutionContext): Future[JobResult] = {
    lock.tryLock(f.map(res => jobResult(res.toString))) map {
      case Some(r) =>
        Logger.info(s"successfully acquired lock for $name - result $r")
        Result(s"$name - ${r.message}")
      case None =>
        Logger.info(s"failed to acquire lock for $name")
        Result(s"$name failed")
    } recover {
      case _: Exception => Result(s"$name failed")
    }
  }
}
