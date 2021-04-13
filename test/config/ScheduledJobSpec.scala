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

package config

import Helpers.SCRSSpec
import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import jobs.SchedulingActor.FireSubscriptions
import jobs.{ScheduledJob, SchedulingActor}
import org.mockito.Mockito._
import org.quartz.CronExpression
import org.scalatest.mockito.MockitoSugar
import play.api.Configuration
import services.SubscriptionFiringService

class ScheduledJobSpec extends SCRSSpec with MockitoSugar {
val jobNameTest = "fooBarAndWizzWithABang"
  val mockService = mock[SubscriptionFiringService]
  val mockActorSystem = mock[ActorSystem]
  val mockQuartzSchedulerExtension = mock[QuartzSchedulerExtension]
  class Setup(cronString:String, enabled: Boolean = false) {
    val stringBecauseBooleansAreWeird:String = if(enabled) "true" else "false"
    reset(
      mockQuartzSchedulerExtension,
      mockService,
      mockActorSystem)
    val job = new ScheduledJob {
      override lazy val scheduledMessage: SchedulingActor.ScheduledMessage[_] = FireSubscriptions(mockService)
      override  val config: Configuration = Configuration(
        s"schedules.$jobNameTest.expression" -> s"$cronString",
        s"schedules.$jobNameTest.enabled" -> s"$stringBecauseBooleansAreWeird",
        s"schedules.$jobNameTest.description" -> "foo bar"
      )
      override lazy val actorSystem: ActorSystem = mockActorSystem
      override lazy val jobName: String = jobNameTest
      override lazy val scheduler = mockQuartzSchedulerExtension
      override lazy val schedulingActorRef = mock[ActorRef]
    }
  }

  "expression should read from string correctly with underscores" in new Setup ("0_0/10_0-23_?_*_*_*") {
    job.expression shouldBe "0 0/10 0-23 ? * * *"
  }
  "expression should read from string correctly with underscores with a different value we will also be using" in new Setup ("0/59_0_0-23_?_*_*_*") {
    job.expression shouldBe "0/59 0 0-23 ? * * *"
  }
  "isValid should return true if valid cron config returned" in new Setup("0_0/2_0-23_?_*_*_*") {
    job.expressionValid shouldBe true
  }
  "isValid should return false if foo is returned as cron config" in new Setup("foo") {
    job.expressionValid shouldBe false
  }
  "isValid should return false if empty string is returned" in new Setup("") {
    job.expressionValid shouldBe false
  }
  "expression once converted should convert to a cron expression success" in new Setup("0/10_0_0-23_?_*_*_*") {
    val parsed = new CronExpression(job.expression)
    parsed.getCronExpression shouldBe "0/10 0 0-23 ? * * *"
    parsed.getExpressionSummary shouldBe """seconds: 0,10,20,30,40,50
                                           |minutes: 0
                                           |hours: 0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23
                                           |daysOfMonth: ?
                                           |months: *
                                           |daysOfWeek: *
                                           |lastdayOfWeek: false
                                           |nearestWeekday: false
                                           |NthDayOfWeek: 0
                                           |lastdayOfMonth: false
                                           |years: *
                                           |""".stripMargin
  }
  "scheduler called if enabled and valid cron config" in new Setup ("0/10_0_0-23_?_*_*_*", true) {
    job.schedule shouldBe true
  }
  "scheduler NOT called if not enabled and cron config invalid" in new Setup ("fudge*", false) {
    job.schedule shouldBe false

  }
  "scheduler NOT called if enabled and cron config invalid" in new Setup ("0/10_0_0-FOO23_?_*_*_*", true) {
    job.schedule shouldBe false
  }
}
