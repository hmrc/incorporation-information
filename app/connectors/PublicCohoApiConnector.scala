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
import uk.gov.hmrc.play.http.{HttpException, _}
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.SCRSFeatureSwitches
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PublicCohoApiConnector @Inject()(config: MicroserviceConfig) extends PublicCohoApiConn {

  def httpProxy = WSHttpProxy
  def httpNoProxy = WSHttp

  val featureSwitch = SCRSFeatureSwitches
  val incorpFrontendStubUrl =  config.incorpFrontendStubUrl
  val cohoPublicUrl =  config.cohoPublicBaseUrl
  val cohoPublicApiAuthToken = config.cohoPublicApiAuthToken
  val cohoStubbedUrl = config.cohoStubbedUrl


}

trait PublicCohoApiConn {

  def httpProxy: HttpGet with WSProxy
  def httpNoProxy: HttpGet

  val featureSwitch: SCRSFeatureSwitches
  val incorpFrontendStubUrl : String
  val cohoPublicUrl: String
  val cohoPublicApiAuthToken: String
  val cohoStubbedUrl:String

  def getCompanyProfile(crn: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoPublicUrl/company/$crn")
      case false => (httpNoProxy, hc, s"$cohoStubbedUrl/company-profile/$crn")
    }


    http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc) map {
      res =>
        res.status match {
          case NO_CONTENT => None
          case _ => Some(res.json)
        }
    } recover handlegetCompanyProfileError(crn)
  }

  def getOfficerList(crn: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoPublicUrl/company/$crn/officers")
      case false => (httpNoProxy, hc, s"$cohoStubbedUrl/company/$crn/officers")
    }

    http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc) map {
      res =>
        res.status match {
          case NO_CONTENT => None
          case _ => Some(res.json)
        }
    } recover handlegetOfficerListError(crn)
  }

  def getOfficerAppointment(officerAppointmentUrl: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoPublicUrl$officerAppointmentUrl")
      case false => (httpNoProxy, hc, s"$cohoStubbedUrl/get-officer-appointment?fn=testFirstNae&sn=testSurname")
    }

    http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc) map {
      res =>
        res.status match {
          case NO_CONTENT =>
            Logger.info(s"[PublicCohoApiConnector] [getOfficerAppointments] - Could not find officer appointment for Officer url  - $officerAppointmentUrl")
            throw new NotFoundException(s"No content for Officer url  - $officerAppointmentUrl")
          case _ => res.json
        }

    } recover handleOfficerAppointmentsError(url)
  }

  private def handlegetCompanyProfileError(crn: String): PartialFunction[Throwable, Option[JsValue]] = {
    case ex: NotFoundException =>
      Logger.info(s"[PublicCohoApiConnector] [getCompanyProfile] - Could not find company data for CRN - $crn")
      None
    case ex: HttpException =>
      Logger.info(s"[PublicCohoApiConnector] [getCompanyProfile] - Returned status code: ${ex.responseCode} for $crn - reason: ${ex.getMessage}")
      None
    case ex: Throwable =>
      Logger.info(s"[PublicCohoApiConnector] [getCompanyProfile] - Failed for $crn - reason: ${ex.getMessage}")
      None
  }

  private def handlegetOfficerListError(crn: String): PartialFunction[Throwable, Option[JsValue]] = {
    case ex: NotFoundException =>
      Logger.info(s"[PublicCohoApiConnector] [getOfficerList] - Could not find officer list for CRN - $crn")
      None
    case ex: HttpException =>
      Logger.info(s"[PublicCohoApiConnector] [getOfficerList] - Returned status code: ${ex.responseCode} for $crn - reason: ${ex.getMessage}")
      None
    case ex: Throwable =>
      Logger.info(s"[PublicCohoApiConnector] [getOfficerList] - Failed for $crn - reason: ${ex.getMessage}")
      None
  }
  private def handleOfficerAppointmentsError(officerId: String): PartialFunction[Throwable, JsValue] = {
    case ex: NotFoundException =>
      Logger.info(s"[PublicCohoApiConnector] [getOfficerAppointments] - Could not find officer appointment for Officer ID  - $officerId")
      throw ex
    case ex: HttpException =>
      Logger.info(s"[PublicCohoApiConnector] [getOfficerAppointments] - Returned status code: ${ex.responseCode} for $officerId - reason: ${ex.getMessage}")
      throw ex
    case ex: Throwable =>
      Logger.info(s"[PublicCohoApiConnector] [getOfficerAppointments] - Failed for $officerId - reason: ${ex.getMessage}")
      throw ex
  }


  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def appendAPIAuthHeader(hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(authorization = Some(Authorization(s"Bearer $cohoPublicApiAuthToken")))
  }
}
