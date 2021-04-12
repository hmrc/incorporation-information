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

package utils

import Helpers.SCRSSpec
import org.joda.time.DateTime
import org.scalatest.BeforeAndAfterEach
import utils.SCRSFeatureSwitches._

class FeatureSwitchSpec extends SCRSSpec with BeforeAndAfterEach {

  override def beforeEach() {
    System.clearProperty("feature.transactionalAPI")
    System.clearProperty("feature.proactiveMonitoring")
    System.clearProperty("feature.test")
  }

  "apply" should {

    "return a constructed BooleanFeatureSwitch if the set system property is a boolean" in {
      System.setProperty("feature.test", "true")

      FeatureSwitch("test") shouldBe BooleanFeatureSwitch("test", enabled = true)
    }

    "create an instance of BooleanFeatureSwitch which inherits FeatureSwitch" in {
      FeatureSwitch("test") shouldBe a[FeatureSwitch]
      FeatureSwitch("test") shouldBe a[BooleanFeatureSwitch]
    }

    "create an instance of EnabledTimedFeatureSwitch which inherits FeatureSwitch" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch("test") shouldBe a[FeatureSwitch]
      FeatureSwitch("test") shouldBe a[TimedFeatureSwitch]
      FeatureSwitch("test") shouldBe a[EnabledTimedFeatureSwitch]
    }

    "return an enabled EnabledTimedFeatureSwitch when only the end datetime is specified and is in the future" in {
      System.setProperty("feature.test", "X_9999-05-08T14:30:00Z")

      FeatureSwitch("test") shouldBe a[EnabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled shouldBe true
    }

    "return a disabled EnabledTimedFeatureSwitch when only the end datetime is specified and is in the past" in {
      System.setProperty("feature.test", "X_2000-05-08T14:30:00Z")

      FeatureSwitch("test") shouldBe a[EnabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled shouldBe false
    }

    "return an enabled EnabledTimedFeatureSwitch when only the start datetime is specified and is in the past" in {
      System.setProperty("feature.test", "2000-05-05T14:30:00Z_X")

      FeatureSwitch("test") shouldBe a[EnabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled shouldBe true
    }

    "return a disabled TimedFeatureSwitch when neither date is specified" in {
      System.setProperty("feature.test", "X_X")

      FeatureSwitch("test").enabled shouldBe false
    }

    "create an instance of DisabledTimedFeatureSwitch which inherits FeatureSwitch" in {
      System.setProperty("feature.test", "!2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch("test") shouldBe a[FeatureSwitch]
      FeatureSwitch("test") shouldBe a[TimedFeatureSwitch]
      FeatureSwitch("test") shouldBe a[DisabledTimedFeatureSwitch]
    }

    "return an enabled DisabledTimedFeatureSwitch when only the end datetime is specified and is in the future" in {
      System.setProperty("feature.test", "!X_9999-05-08T14:30:00Z")

      FeatureSwitch("test") shouldBe a[DisabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled shouldBe false
    }

    "return a disabled DisabledTimedFeatureSwitch when only the end datetime is specified and is in the past" in {
      System.setProperty("feature.test", "!X_2000-05-08T14:30:00Z")

      FeatureSwitch("test") shouldBe a[DisabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled shouldBe true
    }

    "return an enabled DisabledTimedFeatureSwitch when only the start datetime is specified and is in the past" in {
      System.setProperty("feature.test", "!2000-05-05T14:30:00Z_X")

      FeatureSwitch("test") shouldBe a[DisabledTimedFeatureSwitch]
      FeatureSwitch("test").enabled shouldBe false
    }

    "return an enabled DisabledTimedFeatureSwitch when neither date is specified" in {
      System.setProperty("feature.test", "!X_X")

      FeatureSwitch("test").enabled shouldBe true
    }
  }

  "unapply" should {

    "deconstruct a given FeatureSwitch into it's name and a false enabled value if undefined as a system property" in {
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) shouldBe Some("test" -> false)
    }

    "deconstruct a given FeatureSwitch into its name and true if defined as true as a system property" in {
      System.setProperty("feature.test", "true")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) shouldBe Some("test" -> true)
    }

    "deconstruct a given FeatureSwitch into its name and false if defined as false as a system property" in {
      System.setProperty("feature.test", "false")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) shouldBe Some("test" -> false)
    }

    "deconstruct a given TimedFeatureSwitch into its name and enabled flag if defined as a system property" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")
      val fs = FeatureSwitch("test")

      FeatureSwitch.unapply(fs) shouldBe Some("test" -> false)
    }
  }

  "getProperty" should {

    "return a disabled feature switch if the system property is undefined" in {
      FeatureSwitch.getProperty("test") shouldBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return an enabled feature switch if the system property is defined as 'true'" in {
      System.setProperty("feature.test", "true")

      FeatureSwitch.getProperty("test") shouldBe BooleanFeatureSwitch("test", enabled = true)
    }

    "return an enabled feature switch if the system property is defined as 'false'" in {
      System.setProperty("feature.test", "false")

      FeatureSwitch.getProperty("test") shouldBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return a EnabledTimedFeatureSwitch when the set system property is a date" in {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch.getProperty("test") shouldBe a[EnabledTimedFeatureSwitch]
    }

    "return a DisabledTimedFeatureSwitch when the set system property is a date" in {
      System.setProperty("feature.test", "!2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      FeatureSwitch.getProperty("test") shouldBe a[DisabledTimedFeatureSwitch]
    }
  }

  "systemPropertyName" should {

    "append feature. to the supplied string'" in {
      FeatureSwitch.systemPropertyName("test") shouldBe "feature.test"
    }
  }

  "setProperty" should {

    "return a feature switch (testKey, false) when supplied with (testKey, testValue)" in {
      FeatureSwitch.setProperty("test", "testValue") shouldBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return a feature switch (testKey, true) when supplied with (testKey, true)" in {
      FeatureSwitch.setProperty("test", "true") shouldBe BooleanFeatureSwitch("test", enabled = true)
    }
  }

  "enable" should {
    "set the value for the supplied key to 'true'" in {
      val fs = FeatureSwitch("test")
      System.setProperty("feature.test", "false")

      FeatureSwitch.enable(fs) shouldBe BooleanFeatureSwitch("test", enabled = true)
    }
  }

  "disable" should {
    "set the value for the supplied key to 'false'" in {
      val fs = FeatureSwitch("test")
      System.setProperty("feature.test", "true")

      FeatureSwitch.disable(fs) shouldBe BooleanFeatureSwitch("test", enabled = false)
    }
  }

  "dynamic toggling should be supported" in {
    val fs = FeatureSwitch("test")

    FeatureSwitch.disable(fs).enabled shouldBe false
    FeatureSwitch.enable(fs).enabled shouldBe true
  }

  "TimedFeatureSwitch" should {

    val START = "2000-01-23T14:00:00.00Z"
    val END = "2000-01-23T15:30:00.00Z"
    val startDateTime = Some(new DateTime(START))
    val endDatetime = Some(new DateTime(END))

    "be enabled when within the specified time range" in {
      val now = new DateTime("2000-01-23T14:30:00.00Z")

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe false
    }

    "be enabled when current time is equal to the start time" in {
      val now = new DateTime(START)

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe false
    }

    "be enabled when current time is equal to the end time" in {
      val now = new DateTime(END)

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe false
    }

    "be disabled when current time is outside the specified time range" in {
      val now = new DateTime("1900-01-23T12:00:00Z")

      EnabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe false
      DisabledTimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled shouldBe true
    }

    "be disabled when current time is in the future of the specified time range with an unspecified start" in {
      val now = new DateTime("2100-01-23T12:00:00Z")

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe false
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe true
    }

    "be enabled when current time is in the past of the specified time range with an unspecified start" in {
      val now = new DateTime("1900-01-23T12:00:00Z")

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe false
    }

    "be enabled when current time is in the range of the specified time range with an unspecified start" in {
      val now = new DateTime("2000-01-23T14:30:00.00Z")

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe false
    }

    "be enabled when current time is in the future of the specified time range with an unspecified end" in {
      val now = new DateTime("2100-01-23T12:00:00Z")

      EnabledTimedFeatureSwitch("test", startDateTime, None, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", startDateTime, None, now).enabled shouldBe false
    }

    "be disabled when current time is in the past of the specified time range with an unspecified end" in {
      val now = new DateTime("1900-01-23T12:00:00Z")

      EnabledTimedFeatureSwitch("test", startDateTime, None, now).enabled shouldBe false
      DisabledTimedFeatureSwitch("test", startDateTime, None, now).enabled shouldBe true
    }

    "be enabled when current time is in the range of the specified time range with an unspecified end" in {
      val now = new DateTime("2000-01-23T14:30:00.00Z")

      EnabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe true
      DisabledTimedFeatureSwitch("test", None, endDatetime, now).enabled shouldBe false
    }
  }

  "SCRSFeatureSwitches" should {
    "return a disabled feature when the associated system property doesn't exist" in {
      SCRSFeatureSwitches.transactionalAPI.enabled shouldBe false
    }

    "return an enabled feature when the associated system property is true" in {
      FeatureSwitch.enable(SCRSFeatureSwitches.transactionalAPI)

      SCRSFeatureSwitches.transactionalAPI.enabled shouldBe true
    }

    "return a disable feature when the associated system property is false" in {
      FeatureSwitch.disable(SCRSFeatureSwitches.transactionalAPI)

      SCRSFeatureSwitches.transactionalAPI.enabled shouldBe false
    }

    "return a transactionalApi feature if it exists" in {
      System.setProperty("feature.transactionalAPI", "true")

      SCRSFeatureSwitches("transactionalAPI") shouldBe Some(BooleanFeatureSwitch("transactionalAPI", true))
    }

    "return an empty option if the submissionCheck system property doesn't exist when using the apply function" in {
      SCRSFeatureSwitches("transactionalAPI") shouldBe Some(BooleanFeatureSwitch("transactionalAPI", false))
    }

    "return a proactiveMonitoring feature if it exists" in {
      System.setProperty("schedules.proactive-monitoring-job.enabled", "true")

      SCRSFeatureSwitches("proactive-monitoring-job") shouldBe Some(BooleanFeatureSwitch(s"$KEY_PRO_MONITORING", true))
    }

    "return an empty option if the proactiveMonitoring system property doesn't exist when using the apply function" in {
      System.setProperty("schedules.proactive-monitoring-job.enabled", "")
      SCRSFeatureSwitches("proactive-monitoring-job") shouldBe Some(BooleanFeatureSwitch(s"$KEY_PRO_MONITORING", false))
    }
  }
}
