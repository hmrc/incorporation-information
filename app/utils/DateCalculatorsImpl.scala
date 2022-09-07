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

import java.time.format.{DateTimeFormatter, TextStyle}
import java.time.{LocalDateTime, LocalTime, ZoneId, ZoneOffset}
import java.util.Locale
import javax.inject.Inject


class DateCalculatorsImpl @Inject()() extends DateCalculators

trait DateCalculators {

  def getCurrentDay: String = getTheDay(getDateTimeNowUTC)

  def getDateTimeNowUTC: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
  def getDateTimeNowGMT = LocalDateTime.now(ZoneId.of("Europe/London"))

  val cohoStringToDateTime: String => LocalDateTime = (cohoString: String) => if (cohoString.size < 17) {
    throw new Exception(s"timepoint is not 17 characters it is ${cohoString.size}")
  } else {
    LocalDateTime.parse(cohoString, DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
  }
  val dateGreaterThanNow: String => Boolean = (dateToCompare: String) =>
    cohoStringToDateTime(dateToCompare).isAfter(getDateTimeNowGMT)

  def getTheDay(nowDateTime: LocalDateTime): String =
    nowDateTime.getDayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).toUpperCase

  def loggingDay(validLoggingDays: String, todaysDate: String): Boolean = {
    validLoggingDays.split(",").contains(todaysDate)
  }

  def loggingTime(validLoggingTimes: String, now: LocalTime): Boolean = {
    implicit val frmt = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val validTimes = validLoggingTimes.split("_")

    (validTimes.head isBefore now) && (now isBefore validTimes.last)
  }
}
