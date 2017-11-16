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
import models.{IncorpStatusEvent, IncorpUpdateResponse}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import uk.gov.hmrc.http.{ HeaderCarrier, HttpGet, HttpPatch, HttpPost, HttpResponse }

class FiringSubscriptionsConnectorImpl extends FiringSubscriptionsConnector with ServicesConfig {
  val http = WSHttp
}

trait FiringSubscriptionsConnector {
  val http: HttpGet with HttpPost with HttpPatch

  def connectToAnyURL(iuResponse: IncorpUpdateResponse, url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val json = Json.toJson[IncorpUpdateResponse](iuResponse)(IncorpUpdateResponse.writes)
    try {
      http.POST[JsValue, HttpResponse](s"$url", json)
    } catch {
      case ex: Exception =>
        // Exceptions like MalformedUrlException won't be wrapped in a future by default :-(
        Logger.error(s"[FiringSubscriptionsConnector] [connectToAnyURL] - Error posting incorp response to $url - ${ex.getMessage}", ex)
        Future.failed(ex)
    }
  }
}
