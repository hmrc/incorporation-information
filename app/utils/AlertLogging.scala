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

import java.time.LocalTime
import java.time.format.DateTimeFormatter

import play.api.Logger

object PagerDutyKeys extends Enumeration {
  val COHO_TX_API_NOT_FOUND = Value
  val COHO_TX_API_4XX = Value
  val COHO_TX_API_SERVICE_UNAVAILABLE = Value
  val COHO_TX_API_GATEWAY_TIMEOUT = Value
  val COHO_TX_API_5XX = Value
  val COHO_PUBLIC_API_NOT_FOUND = Value
  val COHO_PUBLIC_API_4XX = Value
  val COHO_PUBLIC_API_SERVICE_UNAVAILABLE = Value
  val COHO_PUBLIC_API_GATEWAY_TIMEOUT = Value
  val COHO_PUBLIC_API_5XX = Value
  val TIMEPOINT_INVALID = Value
}

trait AlertLogging {
  val dateCalculators: DateCalculators

  protected val loggingDays: String
  protected val loggingTimes: String

  def pagerduty(key: PagerDutyKeys.Value, message: Option[String] = None) {
    val log = s"${key.toString}${message.fold("")(msg => s" - $msg")}"
    if(inWorkingHours) Logger.error(log) else Logger.info(log)
  }

  def inWorkingHours: Boolean = isLoggingDay && isBetweenLoggingTimes

  private[utils] def today: String = dateCalculators.getCurrentDay

  private[utils] def now: LocalTime = dateCalculators.getCurrentTime

  private[utils] def isLoggingDay = loggingDays.split(",").contains(today)

  private[utils] def isBetweenLoggingTimes: Boolean = {
    val stringToDate = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val Array(start, end) = loggingTimes.split("_") map stringToDate
    ((start isBefore now) || (now equals start)) && (now isBefore end)
  }
}