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

package controllers.test

import Helpers.SCRSSpec
import akka.actor.ActorSystem
import constants.JobNames._
import jobs.SchedulingActor.ScheduledMessage
import jobs.{LockResponse, ScheduledJob, SchedulingActor}
import org.mockito.Matchers.any
import org.mockito.Mockito._
import play.api.Configuration
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.InsertResult
import services.{IncorpUpdateService, SubscriptionFiringService}

import scala.concurrent.{ExecutionContext, Future}


class ManualTriggerControllerSpec extends SCRSSpec {
  val mockIncorpUpdateService = mock[IncorpUpdateService]
  val mockSubFiringService = mock[SubscriptionFiringService]
  val mockSM = mock[ScheduledMessage[Either[InsertResult, LockResponse]]]
  val mockSMSeq = mock[ScheduledMessage[Either[Seq[Boolean], LockResponse]]]

  val mockIncUpdatesJob = new ScheduledJob {
    override val scheduledMessage: SchedulingActor.ScheduledMessage[_] = mockSM
    val iijob = "incorp-update-job"
    override val config: Configuration = Configuration(
      s"schedules.$iijob.expression" -> "0/10_0_0-23_?_*_*_*",
      s"schedules.$iijob.enabled" -> "true",
      s"schedules.$iijob.description" -> "foo bar"
    )
    override val actorSystem: ActorSystem = mock[ActorSystem]
    override val jobName: String = iijob
  }
  val mockFireSubJob = new ScheduledJob {
    override val scheduledMessage: SchedulingActor.ScheduledMessage[_] = mockSMSeq
    val fsjob = "fire-subs-job"
    override val config: Configuration = Configuration(
      s"schedules.$fsjob.expression" -> "0/10_0_0-23_?_*_*_*",
      s"schedules.$fsjob.enabled" -> "true",
      s"schedules.$fsjob.description" -> "foo bar"
    )
    override val actorSystem: ActorSystem = mock[ActorSystem]
    override val jobName: String = fsjob
  }

  class Setup {
    reset(mockSM,mockSMSeq, mockIncorpUpdateService, mockSubFiringService)
    val controller = new ManualTriggerController {
      override protected val incUpdatesJob = mockIncUpdatesJob
      override protected val fireSubJob = mockFireSubJob
      val controllerComponents: ControllerComponents = stubControllerComponents()
    }
  }

  "calling triggerJob" when {

    "supplying the job name incorp-update" should {

      "execute the job and return a message" in new Setup {
        when(mockSM.service).thenReturn(mockIncorpUpdateService)
        when(mockIncorpUpdateService.invoke(any[ExecutionContext]())).thenReturn(Future.successful(Left(InsertResult(1,1))))
        val result = controller.triggerJob(INCORP_UPDATE)(FakeRequest())

        status(result) shouldBe 200
        contentAsString(result) shouldBe Left(InsertResult(1,1)).toString
      }
    }

    "supplying the job name fire-subs" should {

      "execute the job and return a message" in new Setup {
        when(mockSMSeq.service).thenReturn(mockSubFiringService)
        when(mockSubFiringService.invoke(any[ExecutionContext]())).thenReturn(Future.successful(Left(Seq(true))))
        val result = controller.triggerJob(FIRE_SUBS)(FakeRequest())

        status(result) shouldBe 200
        contentAsString(result) shouldBe "Left(List(true))"
      }
    }

    "supplying the job name xxxx" should {

      "return a 404" in new Setup {
        val result = controller.triggerJob("xxxx")(FakeRequest())

        status(result) shouldBe 404
      }
    }
  }
}