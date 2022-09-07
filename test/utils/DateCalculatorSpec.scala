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

import java.time.{LocalDateTime, LocalTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import Helpers.SCRSSpec
import org.scalatest.BeforeAndAfterEach

class DateCalculatorSpec extends SCRSSpec with BeforeAndAfterEach {

  class StandardSetup {
    val dCalc = new DateCalculators {}
  }

  def time(h: Int, m: Int, s: Int) = LocalTime.parse(f"$h%02d" + ":" + f"$m%02d" + ":" + f"$s%02d", DateTimeFormatter.ofPattern("HH:mm:ss"))

  "getTheDay" must {
    "return todays day" in new StandardSetup {
      val testDate = LocalDateTime.parse("2017-07-11T00:00:00.000")
      dCalc.getTheDay(testDate) mustBe "TUE"
    }
  }

  class SetupWithDateTimeOverrides(now: LocalDateTime) {
    val nDCalc = new DateCalculators {
      override def getDateTimeNowGMT = now
    }
  }
  "epochToDateTime" must {
    "convert coho timestamp to the correct datetime" in new StandardSetup {
      val res = dCalc.cohoStringToDateTime("20190107171206152")
      res mustBe LocalDateTime.parse("2019-01-07T17:12:06.152")
      res.toInstant(ZoneOffset.UTC).toEpochMilli mustBe 1546881126152L
      val date = LocalDateTime.parse("2019-01-07T17:12:06.152")
      date mustBe res
    }
    "coho timestamp will throw exception if incorrect format (not long enough)" in new StandardSetup {
      intercept[Exception](dCalc.cohoStringToDateTime("2019010717120615"))
    }
    "coho timestamp will throw exception if unparsable" in new StandardSetup {
      intercept[Exception](dCalc.cohoStringToDateTime("foo bar wizz bang"))
    }

  }
  "dateGreaterThanNow" must {
    val date = LocalDateTime.parse("2017-07-11T00:00:00.005")
    val cohoString = 20170711000000005L
    val cohoStringPlus1 = 20170711000000006L
    val cohoStringMinus1 = 20170711000000004L
    "return true for coho string greater than now" in new SetupWithDateTimeOverrides(now = date) {
      nDCalc.dateGreaterThanNow(cohoStringPlus1.toString) mustBe true
    }
    "return false for coho string equal to now" in new SetupWithDateTimeOverrides(now = date) {
      nDCalc.dateGreaterThanNow(cohoString.toString) mustBe false
    }
    "return false for epoch less than now" in new SetupWithDateTimeOverrides(now = date) {
      nDCalc.dateGreaterThanNow(cohoStringMinus1.toString) mustBe false
    }
  }

  "loggingDate" must {

    "return true if today is a logging day" in new StandardSetup {
      val testDate = "TUE"
      val loggingDays = "MON,TUE,WED,THU,FRI"
      dCalc.loggingDay(loggingDays, testDate) mustBe true
    }
    "return false if today is not logging day" in new StandardSetup {
      val testDate = "SAT"
      val loggingDays = "MON,TUE,WED,THU,FRI"
      dCalc.loggingDay(loggingDays, testDate) mustBe false
    }
    "return true if time now is in range" in new StandardSetup {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(9, 0, 0)
      dCalc.loggingTime(blockageLoggingTime, theTimeNow) mustBe true
    }
    "return true if time now is a second within top range" in new StandardSetup {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(15, 59, 59)
      dCalc.loggingTime(blockageLoggingTime, theTimeNow) mustBe true
    }
    "return true if time now is a second within bottom range" in new StandardSetup {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(7, 0, 1)
      dCalc.loggingTime(blockageLoggingTime, theTimeNow) mustBe true
    }
    "return false if time now is outside the range" in new StandardSetup {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(6, 0, 0)
      dCalc.loggingTime(blockageLoggingTime, theTimeNow) mustBe false
    }
    "return false if time now is on the bottom range" in new StandardSetup {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(7, 0, 0)
      dCalc.loggingTime(blockageLoggingTime, theTimeNow) mustBe false
    }
    "return false if time now is on the top range" in new StandardSetup {
      val blockageLoggingTime = "07:00:00_16:00:00"
      val theTimeNow = time(16, 0, 0)
      dCalc.loggingTime(blockageLoggingTime, theTimeNow) mustBe false
    }
  }
}
