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

package jobs


import akka.actor.ActorSystem
import javax.inject.Inject
import jobs.SchedulingActor.ProactiveMonitoring
import play.api.Configuration
import services.ProactiveMonitoringService


class ProactiveMonitoringJob @Inject()(val config: Configuration,
                                       val proactiveMonitoringService: ProactiveMonitoringService) extends ScheduledJob {
  val jobName = "proactive-monitoring-job"
  val actorSystem = ActorSystem(jobName)
  val scheduledMessage = ProactiveMonitoring(proactiveMonitoringService)
  schedule
}