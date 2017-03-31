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
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.SCRSFeatureSwitches.{KEY_INCORP_UPDATE, KEY_TX_API}

class FeatureSwitchControllerSpec extends SCRSSpec with BeforeAndAfterEach {

  override def beforeEach() {
    System.clearProperty(s"feature.$KEY_INCORP_UPDATE")
    System.clearProperty(s"feature.$KEY_TX_API")
  }

  class Setup {
    val controller = new FeatureSwitchController {}
  }

  "calling switch" should {

    "return an incorp update feature state set to false when we specify stub" in new Setup {
      val featureName = KEY_INCORP_UPDATE
      val featureState = "stub"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe s"BooleanFeatureSwitch($KEY_INCORP_UPDATE,false)"
    }

    "return an incorp update feature state set to true when we specify coho" in new Setup {
      val featureName = KEY_INCORP_UPDATE
      val featureState = "coho"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe s"BooleanFeatureSwitch($KEY_INCORP_UPDATE,true)"
    }

    "return a transactional API feature state set to false when we specify stub" in new Setup {
      val featureName = KEY_TX_API
      val featureState = "stub"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe s"BooleanFeatureSwitch($KEY_TX_API,false)"
    }

    "return a transactional API feature state set to true when we specify coho" in new Setup {
      val featureName = KEY_TX_API
      val featureState = "coho"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe s"BooleanFeatureSwitch($KEY_TX_API,true)"
    }


    "return a transactional API feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = KEY_TX_API
      val featureState = "xxxx"

      val result = controller.switch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe s"BooleanFeatureSwitch($KEY_TX_API,false)"
    }

    "return a incorp update feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = KEY_INCORP_UPDATE
      val featureState = "xxxx"

      val result = controller.switch(featureName, featureState)(FakeRequest())

      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe s"BooleanFeatureSwitch($KEY_INCORP_UPDATE,false)"
    }


    "return an incorp update feature state set to false as a default when we specify a non implemented feature name" in new Setup {
      val featureName = "Rubbish"
      val featureState = "coho"

      val result = controller.switch(featureName, featureState)(FakeRequest())

      status(result) shouldBe BAD_REQUEST


    }

  }

}