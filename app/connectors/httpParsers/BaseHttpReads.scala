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

import connectors.BaseConnector
import play.api.libs.json.Reads
import uk.gov.hmrc.http.{HttpReads, HttpResponse}
import utils.Logging

import scala.util.{Failure, Success, Try}

trait BaseHttpReads extends Logging { _: BaseConnector =>

  val rawReads: HttpReads[HttpResponse] = (_: String, _:String, response: HttpResponse) => response

  def jsonParse[T](response: HttpResponse)(functionName: String,
                                           regId: Option[String] = None,
                                           txId: Option[String] = None)(implicit reads: Reads[T], mf: Manifest[T]): T =
    Try(response.json.as[T]) match {
      case Success(t) => t
      case Failure(e) =>
        logger.error(s"[$functionName] JSON returned could not be parsed to ${mf.runtimeClass.getTypeName} model${logContext(regId, txId)}")
        throw e
    }
}
