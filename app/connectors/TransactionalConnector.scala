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

import javax.inject.Inject

import com.google.inject.ImplementedBy
import config.{MicroserviceConfig, WSHttp, WSHttpProxy}
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.http.{HttpException, NotFoundException, HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.SCRSFeatureSwitches

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait TransactionalAPIResponse
case class SuccessfulTransactionalAPIResponse(js: JsValue) extends TransactionalAPIResponse
case object FailedTransactionalAPIResponse extends TransactionalAPIResponse

class TransactionalConnectorImpl @Inject()(config: MicroserviceConfig) extends TransactionalConnector {
  val featureSwitch = SCRSFeatureSwitches
  val stubUrl = config.IncorpFrontendStubUrl
  val cohoUrl = "get from injected config"  //todo: get from injected config
  lazy val httpProxy = WSHttpProxy
  lazy val httpNoProxy = WSHttp
}

@ImplementedBy(classOf[TransactionalConnectorImpl])
trait TransactionalConnector {

  protected def httpProxy: HttpGet with WSProxy
  protected def httpNoProxy: HttpGet
  protected val featureSwitch: SCRSFeatureSwitches
  protected val stubUrl: String
  protected val cohoUrl: String

  //todo: fetch all from stub
  def fetchTransactionalData(transactionID: String)(implicit hc: HeaderCarrier): Future[TransactionalAPIResponse] = {
    val (http, url) = useProxy match {
      case true => (httpProxy, s"$cohoUrl") //todo: append real coho API path
      case false => (httpNoProxy, s"$stubUrl/incorporation-frontend-stubs/fetch-data/$transactionID")
    }
    http.GET[JsValue](url) map {
      SuccessfulTransactionalAPIResponse
    } recover {
      case ex: NotFoundException => FailedTransactionalAPIResponse
      case ex: HttpException => FailedTransactionalAPIResponse
      case ex: Throwable => FailedTransactionalAPIResponse
    }
    //todo: log errors
  }

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled
}
