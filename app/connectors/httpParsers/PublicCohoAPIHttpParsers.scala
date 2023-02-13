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

package connectors.httpParsers

import connectors._
import models.{IncorpUpdate, IncorpUpdatesResponse}
import play.api.http.Status._
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HttpReads.{is4xx, is5xx}
import uk.gov.hmrc.http.{GatewayTimeoutException, HttpException, HttpReads, HttpResponse, NotFoundException, ServiceUnavailableException, UpstreamErrorResponse}
import utils.{AlertLogging, PagerDutyKeys}

trait PublicCohoAPIHttpParsers extends BaseHttpReads with AlertLogging {
  _: BaseConnector =>

  def getCompanyProfileHttpReads(crn: String, isScrs: Boolean): HttpReads[Option[JsValue]] = (_: String, _: String, response: HttpResponse) =>
    response.status match {
      case OK => Some(response.json)
      case NO_CONTENT =>
        None
      case NOT_FOUND if isScrs =>
        pagerduty(PagerDutyKeys.COHO_PUBLIC_API_NOT_FOUND, Some(s"Could not find company data" + logContext(crn = Some(crn))))
        None
      case NOT_FOUND =>
        logger.info(s"[getCompanyProfileHttpReads] Could not find company data" + logContext(crn = Some(crn)))
        None
      case SERVICE_UNAVAILABLE =>
        pagerduty(PagerDutyKeys.COHO_PUBLIC_API_SERVICE_UNAVAILABLE, Some("Service unavailable" + logContext(crn = Some(crn))))
        None
      case GATEWAY_TIMEOUT =>
        pagerduty(PagerDutyKeys.COHO_PUBLIC_API_GATEWAY_TIMEOUT, Some("Gateway timeout"  + logContext(crn = Some(crn))))
        None
      case status if is4xx(status) =>
        pagerduty(PagerDutyKeys.COHO_PUBLIC_API_4XX, Some(s"Returned status code: $status"  + logContext(crn = Some(crn))))
        None
      case status if is5xx(status) =>
        pagerduty(PagerDutyKeys.COHO_PUBLIC_API_5XX, Some(s"Returned status code: $status"  + logContext(crn = Some(crn))))
        None
      case status =>
        logger.error(s"[getCompanyProfileHttpReads] Returned unexpected status code: $status" + logContext(crn = Some(crn)))
        None
    }

  def getOfficerListHttpReads(crn: String): HttpReads[Option[JsValue]] = (_: String, _: String, response: HttpResponse) =>
    response.status match {
      case OK => Some(response.json)
      case NO_CONTENT =>
        None
      case NOT_FOUND =>
        logger.info(s"[getOfficerListHttpReads] Could not find officer list" + logContext(crn = Some(crn)))
        None
      case status =>
        logger.info(s"[getOfficerListHttpReads] Returned status code: $status" + logContext(crn = Some(crn)))
        None
    }

  def getOfficerAppointmentHttpReads(officerAppointmentUrl: String): HttpReads[JsValue] = (_: String, _: String, response: HttpResponse) =>
    response.status match {
      case OK => response.json
      case NO_CONTENT | NOT_FOUND =>
        logger.info(s"[getOfficerAppointmentHttpReads] Could not find officer appointment for Officer url - $officerAppointmentUrl")
        throw new NotFoundException(s"No content for Officer URL: '$officerAppointmentUrl'")
      case status =>
        val msg = s"[getOfficerAppointmentHttpReads] Returned status code: $status for Officer URL: '$officerAppointmentUrl'"
        logger.info(msg)
        throw UpstreamErrorResponse(msg, status)
    }
}
