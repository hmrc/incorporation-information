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

import javax.inject.{Inject, Singleton}

import org.joda.time.Duration
import play.api.Logger
import services.SubscriptionFiringService
import uk.gov.hmrc.lock.{LockKeeper, LockRepository}
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches
import play.modules.reactivemongo.MongoDbConnection

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FireSubscriptionsJobImpl @Inject()(fireSubsService: SubscriptionFiringService) extends FireSubscriptionsJob {
  val name = "fire-subs-job"
  lazy val subscriptionFiringService = fireSubsService

  override lazy val lock: LockKeeper = new LockKeeper() {
    override val lockId = s"$name-lock"
    override val forceLockReleaseAfter: Duration = lockTimeout
    private implicit val mongo = new MongoDbConnection {}.db
    override val repo = new LockRepository
  }
}

trait FireSubscriptionsJob extends ExclusiveScheduledJob with JobConfig {

  val lock: LockKeeper
  val subscriptionFiringService: SubscriptionFiringService


  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    SCRSFeatureSwitches.scheduler.enabled match {
      case true => {
        lock.tryLock {
          Logger.info(s"Triggered $name")
          subscriptionFiringService.fireIncorpUpdateBatch map { result =>
            val message = s"Feature is turned on - result = ${result}"
            Logger.info(message)
            Result(message)
          }
        } map {
          case Some(x) =>
            Logger.info(s"successfully acquired lock for $name - result ${x}")
            Result(s"$name")
          case None =>
            Logger.info(s"failed to acquire lock for $name")
            Result(s"$name failed")
        } recover {
          case _: Exception => Result(s"$name failed")
        }
      }
      case false => Future.successful(Result(s"Feature is turned off"))
    }
  }

}

