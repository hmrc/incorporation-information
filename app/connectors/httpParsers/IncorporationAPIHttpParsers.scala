/*
 * Copyright 2024 HM Revenue & Customs
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

import connectors.{BaseConnector, FailedTransactionalAPIResponse, IncorpUpdateAPIFailure, SuccessfulTransactionalAPIResponse, TransactionalAPIResponse}
import models.{IncorpUpdate, IncorpUpdatesResponse}
import play.api.http.Status.{GATEWAY_TIMEOUT, NOT_FOUND, NO_CONTENT, OK, SERVICE_UNAVAILABLE}
import uk.gov.hmrc.http.HttpReads.{is4xx, is5xx}
import uk.gov.hmrc.http.{GatewayTimeoutException, HttpReads, HttpResponse, NotFoundException, ServiceUnavailableException, UpstreamErrorResponse}
import utils.{AlertLogging, PagerDutyKeys}

trait IncorporationAPIHttpParsers extends BaseHttpReads with AlertLogging { _: BaseConnector =>

  def checkForIncorpUpdateHttpReads(timepoint: Option[String]): HttpReads[Seq[IncorpUpdate]] = (_: String, _: String, response: HttpResponse) =>
    response.status match {
      case OK =>
        val items = jsonParse[IncorpUpdatesResponse](response)("checkForIncorpUpdateHttpReads").items
        if (
          items exists {
            case IncorpUpdate(_, "accepted", Some(_), Some(_), _, _) => false
            case IncorpUpdate(_, "rejected", _, _, _, _) => false
            case failure =>
              logger.error("[checkForIncorpUpdateHttpReads] CH_UPDATE_INVALID")
              logger.info(s"[checkForIncorpUpdateHttpReads] CH Update failed for transaction ID: ${failure.transactionId}. Status: ${failure.status}, incorpdate provided: ${failure.incorpDate.isDefined}")
              true
          }
        ) {
          Seq()
        } else {
          items
        }
      case NO_CONTENT => Seq()
      case status =>
        val msg = s"[checkForIncorpUpdateHttpReads] request to fetch incorp updates returned a $status. No incorporations were processed for timepoint $timepoint"
        logger.error(msg)
        throw IncorpUpdateAPIFailure(UpstreamErrorResponse.apply(msg, status))
    }

  def checkForIndividualIncorpUpdateHttpReads(timepoint: Option[String]): HttpReads[Seq[IncorpUpdate]] = (_: String, _: String, response: HttpResponse) =>
    response.status match {
      case OK => jsonParse[IncorpUpdatesResponse](response)("checkForIncorpUpdateHttpReads").items
      case NO_CONTENT => Seq()
      case status =>
        val msg = s"[checkForIndividualIncorpUpdateHttpReads] request to fetch incorp updates returned a $status. No incorporations were processed for timepoint $timepoint"
        logger.error(msg)
        throw IncorpUpdateAPIFailure(UpstreamErrorResponse.apply(msg, status))
    }

  def fetchTransactionalDataHttpReads(txId: String): HttpReads[TransactionalAPIResponse] = (_: String, _: String, response: HttpResponse) =>
    response.status match {
      case NOT_FOUND =>
        pagerduty(PagerDutyKeys.COHO_TX_API_NOT_FOUND, Some(s"Could not find incorporation data" + logContext(txId = Some(txId))))
        FailedTransactionalAPIResponse
      case status if is4xx(status) =>
        pagerduty(PagerDutyKeys.COHO_TX_API_4XX, Some(s"$status returned" + logContext(txId = Some(txId))))
        FailedTransactionalAPIResponse
      case GATEWAY_TIMEOUT =>
        pagerduty(PagerDutyKeys.COHO_TX_API_GATEWAY_TIMEOUT, Some("Gateway timeout" + logContext(txId = Some(txId))))
        FailedTransactionalAPIResponse
      case SERVICE_UNAVAILABLE =>
        pagerduty(PagerDutyKeys.COHO_TX_API_SERVICE_UNAVAILABLE, Some("Service unavailable" + logContext(txId = Some(txId))))
        FailedTransactionalAPIResponse
      case status if is5xx(status) =>
        pagerduty(PagerDutyKeys.COHO_TX_API_5XX, Some(s"Returned status code: $status" + logContext(txId = Some(txId))))
        FailedTransactionalAPIResponse
      case _ =>
        SuccessfulTransactionalAPIResponse(response.json)
    }
}
