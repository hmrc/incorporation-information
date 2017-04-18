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

package controllers.test

import Helpers.SCRSSpec
import play.api.test.FakeRequest
import uk.gov.hmrc.play.scheduling.ScheduledJob
import org.mockito.Mockito._
import org.mockito.Matchers.any
import scala.concurrent.Future
import constants.JobNames._

class ManualTriggerControllerSpec extends SCRSSpec {

  val mockScheduledJob = mock[ScheduledJob]

  class Setup {
    val controller = new ManualTriggerController {
      override protected val incUpdatesJob = mockScheduledJob
    }
  }

  "calling triggerJob" when {

    "supplying the job name incorp-update" should {

      "execute the job and return a message" in new Setup {
        when(mockScheduledJob.execute(any())).thenReturn(Future.successful(mockScheduledJob.Result("anything")))
        val result = await(controller.triggerJob(INCORP_UPDATE)(FakeRequest()))

        status(result) shouldBe 200
        bodyOf(result) shouldBe "anything"
      }
    }
  }
}
