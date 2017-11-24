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

import DateCalculators.{getCurrentDay, getCurrentTime}
import play.api.Logger

trait AlertLogging {

  protected val loggingDays: String
  protected val loggingTimes: String

  def inWorkingHours: Boolean = isLoggingDay && isBetweenLoggingTimes

  def alertCohoTxAPINotFound(): Unit = ifInWorkingHours(Logger.error("COHO_TX_API_NOT_FOUND"))
  def alertCohoTxAPI4xx(): Unit = ifInWorkingHours(Logger.error("COHO_TX_API_4XX"))
  def alertCohoTxAPIServiceUnavailable(): Unit = ifInWorkingHours(Logger.error("COHO_TX_API_SERVICE_UNAVAILABLE"))
  def alertCohoTxAPIGatewayTimeout(): Unit = ifInWorkingHours(Logger.error("COHO_TX_API_GATEWAY_TIMEOUT"))
  def alertCohoTxAPI5xx(): Unit = ifInWorkingHours(Logger.error("COHO_TX_API_5XX"))

  def alertCohoPublicAPINotFound(): Unit = ifInWorkingHours(Logger.error("COHO_PUBLIC_API_NOT_FOUND"))
  def alertCohoPublicAPI4xx(): Unit = ifInWorkingHours(Logger.error("COHO_PUBLIC_API_4XX"))
  def alertCohoPublicAPIServiceUnavailable(): Unit = ifInWorkingHours(Logger.error("COHO_PUBLIC_API_SERVICE_UNAVAILABLE"))
  def alertCohoPublicAPIGatewayTimeout(): Unit = ifInWorkingHours(Logger.error("COHO_PUBLIC_API_GATEWAY_TIMEOUT"))
  def alertCohoPublicAPI5xx(): Unit = ifInWorkingHours(Logger.error("COHO_PUBLIC_API_5XX"))

  private[utils] def today: String = getCurrentDay

  private[utils] def now: LocalTime = getCurrentTime

  private[utils] def ifInWorkingHours(alert: => Unit): Unit = if(inWorkingHours) alert else ()

  private[utils] def isLoggingDay = loggingDays.split(",").contains(today)

  private[utils] def isBetweenLoggingTimes: Boolean = {
    implicit val format: String => LocalTime = LocalTime.parse(_: String, DateTimeFormatter.ofPattern("HH:mm:ss"))
    val Array(start, end) = loggingTimes.split("_")
    (start isBefore now) && (now isBefore end)
  }
}
