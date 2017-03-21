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

package connectors

import config.WSHttp
//import models.SubmissionCheckResponse
import play.api.Logger
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NoStackTrace

// TODO - II-INCORP - Need this connector

object IncorporationCheckAPIConnector extends IncorporationCheckAPIConnector with ServicesConfig {
  override val proxyUrl = baseUrl("company-registration-frontend")
  override val http = WSHttp
}

class SubmissionAPIFailure extends NoStackTrace

trait IncorporationCheckAPIConnector {

  val proxyUrl: String
  val http: HttpGet with HttpPost

  def logError(ex: HttpException, timepoint: Option[String]) = {
    Logger.error(s"[IncorporationCheckAPIConnector] [checkSubmission]" +
      s" request to SubmissionCheckAPI returned a ${ex.responseCode}. " +
      s"No incorporations were processed for timepoint ${timepoint} - Reason = ${ex.getMessage}")
  }

  // TODO - II-INCORP - refactor the recover block - move to a separate method to provide more clarity
  def checkSubmission(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[SubmissionCheckResponse] = {
    val tp = timepoint.fold("")(t => s"timepoint=$t&")
    http.GET[SubmissionCheckResponse](s"$proxyUrl/internal/check-submission?${tp}items_per_page=1") map {
      res => res
    } recover {
      case ex: BadRequestException =>
        logError(ex, timepoint)
        throw new SubmissionAPIFailure
      case ex: NotFoundException =>
        logError(ex, timepoint)
        throw new SubmissionAPIFailure
      case ex: Upstream4xxResponse =>
        Logger.error("[IncorporationCheckAPIConnector] [checkSubmission]" + ex.upstreamResponseCode + " " + ex.message)
        throw new SubmissionAPIFailure
      case ex: Upstream5xxResponse =>
        Logger.error("[IncorporationCheckAPIConnector] [checkSubmission]" + ex.upstreamResponseCode + " " + ex.message)
        throw new SubmissionAPIFailure
      case ex: Exception =>
        Logger.error("[IncorporationCheckAPIConnector] [checkSubmission]" + ex)
        throw new SubmissionAPIFailure
    }
  }
}
