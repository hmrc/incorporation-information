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

package utils

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import org.joda.time.{DateTime, DateTimeZone}


object DateCalculators {

  def getCurrentDay: String = {
    DateTime
      .now(DateTimeZone.UTC)
      .dayOfWeek()
      .getAsText()
      .substring(0,3)
      .toUpperCase
  }

  def getCurrentTime: LocalTime = LocalTime.now

  def getTheDay(nowDateTime: DateTime): String = {
    nowDateTime.dayOfWeek().getAsText().substring(0,3).toUpperCase
  }

  def loggingDay(validLoggingDays: String,todaysDate: String): Boolean = {
    validLoggingDays.split(",").contains(todaysDate)
  }

  def loggingTime(validLoggingTimes: String, now: LocalTime): Boolean = {
    implicit val frmt = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val validTimes = validLoggingTimes.split("_")

    (validTimes.head isBefore now) && (now isBefore validTimes.last)
  }
}
