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

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import jobs.SchedulingActor.ScheduledMessage
import org.quartz.CronExpression
import play.api.{Configuration, Logger}

trait ScheduledJob {
  val scheduledMessage: ScheduledMessage[_]
  val config: Configuration
  val actorSystem: ActorSystem
  val jobName: String

  lazy val scheduler: QuartzSchedulerExtension = QuartzSchedulerExtension(actorSystem)

  lazy val schedulingActorRef: ActorRef = actorSystem.actorOf(SchedulingActor.props)

  def enabled: Boolean = config.getBoolean(s"schedules.$jobName.enabled").getOrElse(false)

  lazy val description: Option[String] = config.getString(s"schedules.$jobName.description")

  lazy val expression: String = config.getString(s"schedules.$jobName.expression") map (_.replaceAll("_", " ")) getOrElse ""

  lazy val expressionValid = CronExpression.isValidExpression(expression)

  lazy val schedule: Boolean = {
    (enabled, expressionValid) match {
      case (true, true) =>
        scheduler.createSchedule(jobName, description, expression)
        scheduler.schedule(jobName, schedulingActorRef, scheduledMessage)
        Logger.info(s"Scheduler for $jobName has been started")
        true
      case (true, false) =>
        Logger.info(s"Scheduler for $jobName is disabled as there is no valid quartz expression: $expression")
        false
      case (false, _) =>
        Logger.info(s"Scheduler for $jobName is disabled by configuration")
        false
    }
  }
}