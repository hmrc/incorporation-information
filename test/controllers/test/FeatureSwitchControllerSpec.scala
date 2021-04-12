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
import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension
import jobs.ScheduledJob
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.SCRSFeatureSwitches.{KEY_INCORP_UPDATE, KEY_TX_API}


class FeatureSwitchControllerSpec extends SCRSSpec with BeforeAndAfterEach {

  override def beforeEach() {
    System.clearProperty(s"feature.$KEY_TX_API")
  }
  val mockSched =  mock[ScheduledJob]
  val mockQuartz = mock[QuartzSchedulerExtension]
  class Setup {
    reset(mockSched)
    reset(mockQuartz)
    val controller = new FeatureSwitchController {
      override val incUpdatesJob: ScheduledJob = mockSched
      override val metricsJob: ScheduledJob = mockSched
      override val fireSubsJob: ScheduledJob = mockSched
      override val proJob: ScheduledJob = mockSched
      val controllerComponents: ControllerComponents = stubControllerComponents()
    }
  }

  "calling switch" should {

    def message(name: String, state: String) = s"[FeatureSwitch] Feature switch for $name is now $state"

    "return an incorp update feature state set to false when we specify stub" in new Setup {
      val featureName = KEY_INCORP_UPDATE
      val featureState = "stub"
      when(mockSched.scheduler).thenReturn(mockQuartz)
      when(mockQuartz.suspendJob(any())).thenReturn(false)

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe message(featureName, "false")
    }

    "return an incorp update feature state set to true when we specify coho" in new Setup {
      val featureName = KEY_INCORP_UPDATE
      val featureState = "coho"
      when(mockSched.scheduler).thenReturn(mockQuartz)
      when(mockQuartz.resumeJob(any())).thenReturn(false)

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe message(featureName, "true")
    }

    "return a transactional API feature state set to false when we specify stub" in new Setup {
      val featureName = KEY_TX_API
      val featureState = "stub"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe message(featureName, "false")
    }

    "return a transactional API feature state set to true when we specify coho" in new Setup {
      val featureName = KEY_TX_API
      val featureState = "coho"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe message(featureName, "true")
    }

    "return a transactional API feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = KEY_TX_API
      val featureState = "xxxx"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe message(featureName, "false")

    }

    "return a incorp update feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = KEY_INCORP_UPDATE
      val featureState = "xxxx"
      when(mockSched.scheduler).thenReturn(mockQuartz)
      when(mockQuartz.resumeJob(any())).thenReturn(false)

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) shouldBe message(featureName, "false")
    }
  }
}
