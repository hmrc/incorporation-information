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

package jobs

import akka.actor.ActorSystem
import jobs.SchedulingActor.UpdateSubscriptionMetrics
import play.api.Configuration
import services.MetricsService

import javax.inject.Inject

class MetricsJob @Inject()(val config: Configuration,
                           val metricsService: MetricsService) extends ScheduledJob {
  val jobName = "metrics-job"
  val actorSystem = ActorSystem(jobName)
  val scheduledMessage = UpdateSubscriptionMetrics(metricsService)
  schedule
}