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
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.play.http.{HttpException, NotFoundException, HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.SCRSFeatureSwitches

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

sealed trait TransactionalResponse
case class SuccessfulTransactionalResponse(js: JsValue) extends TransactionalResponse
case object FailedTransactionalResponse extends TransactionalResponse

//todo: bind in module
class TransactionalConnectorImpl @Inject()(config: MicroserviceConfig) extends TransactionalConnector {
  //todo: DI feature switch
  val featureSwitch = SCRSFeatureSwitches
  val stubUrl = config.IncorpFrontendStubUrl
  val cohoUrl = "get from injected config"  //todo: get from injected config
  lazy val httpProxy = WSHttpProxy
  lazy val httpNoProxy = WSHttp
}

@ImplementedBy(classOf[TransactionalConnectorImpl])
trait TransactionalConnector {

  val featureSwitch: SCRSFeatureSwitches
  def httpProxy: HttpGet with WSProxy
  def httpNoProxy: HttpGet
  val stubUrl: String
  val cohoUrl: String

  //todo: fetch all from stub
  def fetchTransactionalData(transactionID: String)(implicit hc: HeaderCarrier): Future[TransactionalResponse] = {
    val (http, url) = useProxy match {
      case true => (httpProxy, s"$cohoUrl") //todo: append real coho API path
      case false => (httpNoProxy, s"$stubUrl/incorporation-frontend-stubs/fetch-data/$transactionID")
    }
    http.GET[JsValue](url) map {
      SuccessfulTransactionalResponse
    } recover {
      case ex: NotFoundException => FailedTransactionalResponse
      case ex: HttpException => FailedTransactionalResponse
      case ex: Throwable => FailedTransactionalResponse
    }
    //todo: recover from errors - maybe return sealed trait type that other functions can match against
  }

  //todo get company profile from trans data
  def fetchCompanyProfile(transactionID: String)(implicit hc: HeaderCarrier) = {
    fetchTransactionalData(transactionID)
    //todo: pull from json with a jspath find - (js \ "path-to-json")
  }

  private[connectors] def useProxy: Boolean = {
    featureSwitch.transactionalAPI.enabled
  }
}
