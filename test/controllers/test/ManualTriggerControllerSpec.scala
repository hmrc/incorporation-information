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

package controllers.test

import Helpers.SCRSSpec
import play.api.test.FakeRequest
import uk.gov.hmrc.play.scheduling.ScheduledJob
import org.mockito.Mockito._
import org.mockito.Matchers.any
import scala.concurrent.Future
import constants.JobNames._

class ManualTriggerControllerSpec extends SCRSSpec {

  val mockIncUpdatesJob = mock[ScheduledJob]
  val mockFireSubJob = mock[ScheduledJob]

  class Setup {
    val controller = new ManualTriggerController {
      override protected val incUpdatesJob = mockIncUpdatesJob
      override protected val fireSubJob = mockFireSubJob
    }
  }

  "calling triggerJob" when {

    "supplying the job name incorp-update" should {

      "execute the job and return a message" in new Setup {
        when(mockIncUpdatesJob.execute(any())).thenReturn(Future.successful(mockIncUpdatesJob.Result("anything")))
        val result = await(controller.triggerJob(INCORP_UPDATE)(FakeRequest()))

        status(result) shouldBe 200
        bodyOf(result) shouldBe "anything"
      }
    }

    "supplying the job name fire-subs" should {

      "execute the job and return a message" in new Setup {
        when(mockFireSubJob.execute(any())).thenReturn(Future.successful(mockFireSubJob.Result("anything")))
        val result = await(controller.triggerJob(FIRE_SUBS)(FakeRequest()))

        status(result) shouldBe 200
        bodyOf(result) shouldBe "anything"
      }
    }

    "supplying the job name xxxx" should {

      "return a 404" in new Setup {
        val result = await(controller.triggerJob("xxxx")(FakeRequest()))

        status(result) shouldBe 404
      }
    }
  }
}
