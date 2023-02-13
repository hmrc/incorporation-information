/*
 * Copyright 2023 HM Revenue & Customs
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

  def cohoStringToDateTime(cohoString: String): LocalDateTime = if (cohoString.size < 17) {
    throw new Exception(s"timepoint is not 17 characters it is ${cohoString.size}")
  } else {

    // Bespoke extraction due to issue with DateTimeFormatter in Java8. Moving to Java11 may resolve this and can be re-written back to:
    //    val cohoFormat = DateTimeFormatter.ofPattern("uuuuMMddHHmmssSSS")
    //    LocalDateTime.parse(cohoString, cohoFormat)
    val year = cohoString.substring(0,4).toInt
    val month = cohoString.substring(4,6).toInt
    val day = cohoString.substring(6,8).toInt
    val hours = cohoString.substring(8,10).toInt
    val minutes = cohoString.substring(10,12).toInt
    val seconds = cohoString.substring(12,14).toInt
    val nanos = "%-9s".format(cohoString.substring(14,17)).replace(" ", "0").toInt

    LocalDateTime.of(year, month, day, hours, minutes, seconds, nanos)
  }

  def dateGreaterThanNow(dateToCompare: String) =
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
