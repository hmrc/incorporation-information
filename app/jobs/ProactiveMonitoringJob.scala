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

import services.ProactiveMonitoringService
import uk.gov.hmrc.play.scheduling.ExclusiveScheduledJob
import utils.SCRSFeatureSwitches
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DefaultDB

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class ProactiveMonitoringJobImpl @Inject()(val proactiveMonitoringService: ProactiveMonitoringService) extends ProactiveMonitoringJob {
  val name = "proactive-monitoring-job"
  lazy val db: () => DefaultDB = new MongoDbConnection{}.db
}

trait ProactiveMonitoringJob extends ExclusiveScheduledJob with JobConfig with JobHelper {

  val proactiveMonitoringService: ProactiveMonitoringService

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def executeInMutex(implicit ec: ExecutionContext): Future[Result] = {
    ifFeatureEnabled(SCRSFeatureSwitches.proactiveMonitoring){
      whenLockAcquired(proactiveMonitoringService.pollAPIs)
    }
  }
}

