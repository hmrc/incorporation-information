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

package connectors

import connectors.httpParsers.BaseHttpReads
import models.IncorpUpdateResponse
import uk.gov.hmrc.http.{HttpClient, _}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FiringSubscriptionsConnector @Inject()(val http: HttpClient)
                                            (implicit val ec: ExecutionContext) extends BaseConnector with BaseHttpReads {

  def connectToAnyURL(iuResponse: IncorpUpdateResponse, url: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    try {
      http.POST[IncorpUpdateResponse, HttpResponse](s"$url", iuResponse)(IncorpUpdateResponse.writes, rawReads, hc, ec)
    } catch {
      case ex: Exception =>
        logger.error(s"[connectToAnyURL] - Error posting incorp response to $url - ${ex.getMessage}", ex)
        Future.failed(ex)
    }
}