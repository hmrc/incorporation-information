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

import config.{MicroserviceConfig, WSHttp, WSHttpProxy}
import play.api.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.ws.WSProxy
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpException, HttpGet, NotFoundException}
import utils.SCRSFeatureSwitches

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait TransactionalAPIResponse

case class SuccessfulTransactionalAPIResponse(js: JsValue) extends TransactionalAPIResponse

case object FailedTransactionalAPIResponse extends TransactionalAPIResponse

class TransactionalConnectorImpl @Inject()(config: MicroserviceConfig) extends TransactionalConnector {
  val featureSwitch = SCRSFeatureSwitches
  val stubUrl = config.incorpFrontendStubUrl
  val cohoUrl = config.companiesHouseUrl
  val cohoApiAuthToken = config.incorpUpdateCohoApiAuthToken
  lazy val httpProxy = WSHttpProxy
  lazy val httpNoProxy = WSHttp
}

trait TransactionalConnector {

  protected def httpProxy: HttpGet with WSProxy

  protected def httpNoProxy: HttpGet

  protected val featureSwitch: SCRSFeatureSwitches
  protected val stubUrl: String
  protected val cohoUrl: String
  protected val cohoApiAuthToken: String

  def fetchTransactionalData(transactionID: String)(implicit hc: HeaderCarrier): Future[TransactionalAPIResponse] = {
    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc, cohoApiAuthToken), s"$cohoUrl/submissionData/$transactionID")
      case false => (httpNoProxy, hc, s"$stubUrl/incorporation-frontend-stubs/fetch-data/$transactionID")
    }

    //curl -vk -H 'Authorization: Bearer FutU3YcOky_LWCVEnsM3fYjFPxIvoe9ar-l0WBc9' "https://ewfgonzo.companieshouse.gov.uk/submissionData/000-033767"

    http.GET[JsValue](url) map {
      SuccessfulTransactionalAPIResponse
    } recover {
      case ex: NotFoundException =>
        Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Could not find incorporation data for transaction ID - $transactionID")
        FailedTransactionalAPIResponse
      case ex: HttpException =>
        Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - GET of $url returned status code: ${ex.responseCode} for $transactionID - reason: ${ex.getMessage}")
        FailedTransactionalAPIResponse
      case ex: Throwable =>
        Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - GET of $url failed for $transactionID - reason: ${ex.getMessage}")
        FailedTransactionalAPIResponse
    }
  }

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def appendAPIAuthHeader(hc: HeaderCarrier, token: String): HeaderCarrier = {
    hc.copy(authorization = Some(Authorization(s"Bearer $token")))
  }
}

