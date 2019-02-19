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

import javax.inject.{Inject, Singleton}
import org.joda.time.Duration
import play.api.Logger
import play.modules.reactivemongo.MongoDbConnection
import services.IncorpUpdateService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IncorpUpdatesJobImpl @Inject()(injService: IncorpUpdateService) extends IncorpUpdatesJob {
  val name = "incorp-updates-job"
  lazy val incorpUpdateService = injService

  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo = new MongoDbConnection {}.db
    override val repo = new LockRepository
  }
}

trait IncorpUpdatesJob extends ExclusiveScheduledJob with JobConfig {

  val lock: LockKeeper
  val incorpUpdateService: IncorpUpdateService


  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    SCRSFeatureSwitches.scheduler.enabled match {
      case true => {
        lock.tryLock {
          Logger.info(s"Triggered $name")
          incorpUpdateService.updateNextIncorpUpdateJobLot(HeaderCarrier()) map { result =>
            val message = s"Feature incorpUpdate is turned on - result = ${result}"
            Logger.info(message)
            Result(message)
          }
        } map {
          case Some(r) =>
            Logger.info(s"successfully acquired lock for $name - result ${r}")
            Result(s"$name - ${r.message}")
          case None =>
            Logger.info(s"failed to acquire lock for $name")
            Result(s"$name failed")
        } recover {
          case e: Exception =>
            Logger.error(s"[IncorpUpdatesJob][executeInMutex] Exception occured: ${e.getMessage}", e)
            Result(s"$name failed")
        }
      }
      case false => Future.successful(Result(s"Feature incorpUpdate is turned off"))
    }
  }
}

