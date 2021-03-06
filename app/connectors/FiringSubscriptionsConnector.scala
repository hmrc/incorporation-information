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

package connectors

import models.IncorpUpdateResponse
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FiringSubscriptionsConnectorImpl @Inject()(val http: HttpClient)
                                                (implicit val ec: ExecutionContext) extends FiringSubscriptionsConnector {

}

trait FiringSubscriptionsConnector {
  val http: HttpClient
  implicit val ec: ExecutionContext

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