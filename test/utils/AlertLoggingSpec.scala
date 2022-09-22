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

package utils

import java.time.LocalTime

import Helpers.{LogCapturing, SCRSSpec}
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import org.scalatest.concurrent.Eventually
import play.api.Logger

class AlertLoggingSpec extends SCRSSpec with LogCapturing with Eventually {

  val defaultLoggingDays = "MON,TUE,WED,THU,FRI"
  val defaultLoggingTime = "08:00:00_17:00:00"

  val monday = "MON"
  val friday = "FRI"
  val saturday = "SAT"
  val sunday = "SUN"
  val _2pm: LocalTime = LocalTime.parse("14:00:00")
  val _5pm: LocalTime = LocalTime.parse("17:00:00")
  val _9pm: LocalTime = LocalTime.parse("21:00:00")
  val _4_59pm: LocalTime = LocalTime.parse("16:59:59")
  val _8am: LocalTime = LocalTime.parse("08:00:00")
  val _7_59am: LocalTime = LocalTime.parse("07:59:59")
  val _8_01am: LocalTime = LocalTime.parse("08:01:00")

  class Setup(todayForTest: String,
              nowForTest: LocalTime,
              logDays: String = defaultLoggingDays,
              logTimes: String = defaultLoggingTime) {

    object AlertLogging extends AlertLogging {
      override val dateCalculators: DateCalculators = new DateCalculators {}
      protected val loggingTimes: String = logTimes
      protected val loggingDays: String = logDays

      override private[utils] def today = todayForTest
      override private[utils] def now = nowForTest
    }
  }

  class SetupInWorkingHours extends Setup(monday, _2pm)
  class SetupNotInWorkingHours extends Setup(saturday, _9pm)

  "isLoggingDay" must {

    "return true when today is the logging day" in new SetupInWorkingHours {
      AlertLogging.isLoggingDay mustBe true
    }

    "return false when today is not the logging day" in new Setup(saturday, _2pm) {
      AlertLogging.isLoggingDay mustBe false
    }
  }

  "isBetweenLoggingTimes" must {

    "return true when now is between the logging times" in new SetupInWorkingHours {
      AlertLogging.isBetweenLoggingTimes mustBe true
    }

    "return false when now is not between the logging times" in new Setup(monday, _9pm) {
      AlertLogging.isBetweenLoggingTimes mustBe false
    }
  }

  "inWorkingHours" must {

    "return true" when {

      "the current time is 14:00 on a Monday" in new SetupInWorkingHours {
        AlertLogging.inWorkingHours mustBe true
      }

      "the current time is 08:00 on a Monday" in new Setup(monday, _8am) {
        AlertLogging.inWorkingHours mustBe true
      }

      "the current time is 08:01 on a Monday" in new Setup(monday, _8_01am) {
        AlertLogging.inWorkingHours mustBe true
      }

      "the current time is 16:59 on a Friday" in new Setup(friday, _4_59pm) {
        AlertLogging.inWorkingHours mustBe true
      }
    }

    "return false" when {

      "the current time is 07:59:59 on a Monday" in new Setup(monday, _7_59am) {
        AlertLogging.inWorkingHours mustBe false
      }

      "the current time is 17:00 on a Monday" in new Setup(monday, _5pm) {
        AlertLogging.inWorkingHours mustBe false
      }

      "the current time is 21:00 on a Monday" in new Setup(monday, _9pm) {
        AlertLogging.inWorkingHours mustBe false
      }

      "the current time is 14:00 on a Saturday" in new Setup(saturday, _2pm) {
        AlertLogging.inWorkingHours mustBe false
      }

      "the current time is 14:00 on a Sunday" in new Setup(sunday, _2pm) {
        AlertLogging.inWorkingHours mustBe false
      }

    }
  }

   "pager duty" must {
     val validKeys = List(
       PagerDutyKeys.COHO_TX_API_NOT_FOUND,
       PagerDutyKeys.COHO_TX_API_4XX
     )

     def found(logs: List[ILoggingEvent])(count: Int, msg: String, level: Level) = {
       logs.size mustBe count
       logs.head.getMessage mustBe s"[AlertLogging] ${msg}"
       logs.head.getLevel mustBe level
     }
     "accept any Pager Duty key" in new Setup(monday, _8am){
       validKeys foreach { key =>
         withCaptureOfLoggingFrom(AlertLogging.logger) { logs =>
           AlertLogging.pagerduty(key)
           logs.head.getMessage mustBe s"[AlertLogging] ${key.toString}"
         }
       }
     }

     "log an error in working hours" when {
       "custom message is not provided" in new Setup(monday, _8am) {
         validKeys foreach { key =>
           withCaptureOfLoggingFrom(AlertLogging.logger) { logs =>
             AlertLogging.pagerduty(key)
             found(logs)(1, key.toString, Level.ERROR)
           }
         }
       }
       "custom message is provided" in new Setup(friday, _4_59pm) {
         withCaptureOfLoggingFrom(AlertLogging.logger) { logs =>
           AlertLogging.pagerduty(PagerDutyKeys.COHO_TX_API_NOT_FOUND, message = Some("test"))
           found(logs)(1, "COHO_TX_API_NOT_FOUND - test", Level.ERROR)
         }
       }
     }
     "log info out of working hours" when {
       "custom message is not provided" in new Setup(monday, _7_59am) {
         withCaptureOfLoggingFrom(AlertLogging.logger) { logs =>
           AlertLogging.pagerduty(PagerDutyKeys.COHO_TX_API_NOT_FOUND)
           found(logs)(1, "COHO_TX_API_NOT_FOUND", Level.INFO)
         }
       }
       "custom message is provided" in new Setup(friday, _5pm) {
         withCaptureOfLoggingFrom(AlertLogging.logger) { logs =>
           AlertLogging.pagerduty(PagerDutyKeys.COHO_TX_API_NOT_FOUND, message = Some("extra string"))
           found(logs)(1, "COHO_TX_API_NOT_FOUND - extra string", Level.INFO)
         }
       }
     }
     "log info when it is not a weekday" in new Setup(saturday, _2pm) {
       withCaptureOfLoggingFrom(AlertLogging.logger) { logs =>
         AlertLogging.pagerduty(PagerDutyKeys.COHO_PUBLIC_API_4XX)
         found(logs)(1, "COHO_PUBLIC_API_4XX", Level.INFO)
       }
     }
   }
}
