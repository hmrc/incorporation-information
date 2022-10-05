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

import com.codahale.metrics.Counter
import config.{MicroserviceConfig, WSHttpProxy}
import utils.Logging
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.http.ws.WSProxy
import utils._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PublicCohoApiConnectorImpl @Inject()(config: MicroserviceConfig, injMetricsService: MetricsService,
                                           val dateCalculators: DateCalculators,
                                           val httpProxy: WSHttpProxy,
                                           val httpNoProxy: HttpClient
                                          )(implicit val ec: ExecutionContext) extends PublicCohoApiConnector {


  protected val featureSwitch: SCRSFeatureSwitches.type = SCRSFeatureSwitches

  protected val incorpFrontendStubUrl: String = config.incorpFrontendStubUrl
  protected val cohoPublicUrl: String = config.cohoPublicBaseUrl
  protected val cohoPublicApiAuthToken: String = config.cohoPublicApiAuthToken
  protected val nonSCRSPublicApiAuthToken: String = config.nonSCRSPublicApiAuthToken
  protected val cohoStubbedUrl: String = config.cohoStubbedUrl

  protected lazy val metrics: MetricsService = injMetricsService
  protected lazy val successCounter: Counter = metrics.publicCohoApiSuccessCounter
  protected lazy val failureCounter: Counter = metrics.publicCohoApiFailureCounter

  protected val loggingDays: String = config.noRegisterAnInterestLoggingDay
  protected val loggingTimes: String = config.noRegisterAnInterestLoggingTime
}

trait PublicCohoApiConnector extends AlertLogging with Logging {

  implicit val ec: ExecutionContext

  protected def httpProxy: CoreGet with WSProxy

  protected def httpNoProxy: CoreGet

  protected val featureSwitch: SCRSFeatureSwitches
  protected val incorpFrontendStubUrl: String
  protected val cohoPublicUrl: String
  protected val cohoPublicApiAuthToken: String
  protected val nonSCRSPublicApiAuthToken: String
  protected val cohoStubbedUrl: String
  protected val metrics: MetricsService
  protected val successCounter: Counter
  protected val failureCounter: Counter

  def getCompanyProfile(crn: String,
                        isScrs: Boolean = true
                       )(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, extraHeaders, url) = if (useProxy) {
      (httpProxy, createAPIAuthHeader(isScrs), s"$cohoPublicUrl/company/$crn")
    } else {
      (httpNoProxy, Seq(), s"$cohoStubbedUrl/company-profile/$crn")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter), Some(metrics.publicAPITimer.time())) {
      http.GET[HttpResponse](url = url, headers = extraHeaders).map {
        res =>
          res.status match {
            case NO_CONTENT => None
            case _ => Some(res.json)
          }
      }
    } recover handleGetCompanyProfileError(crn, isScrs)
  }

  def getOfficerList(crn: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, extraHeaders, url) = if (useProxy) {
      (httpProxy, createAPIAuthHeader(), s"$cohoPublicUrl/company/$crn/officers")
    } else {
      (httpNoProxy, Seq(), s"$cohoStubbedUrl/company/$crn/officers")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter), Some(metrics.publicAPITimer.time())) {
      http.GET[HttpResponse](url = url, headers = extraHeaders).map {
        res =>
          res.status match {
            case NO_CONTENT => None
            case _ => Some(res.json)
          }
      }
    } recover handleGetOfficerListError(crn)
  }


  def getStubbedFirstAndLastName(url: String): (String, String) = {
    val nUrl = url.replace("appointments", "").replaceAll("[`*{}\\[\\]()>#+:~'%^&@<?;,\"!$=|./_]", "")
    nUrl.length match {
      case x if x > 15 => (nUrl.takeRight(15), nUrl.take(15))
      case _ => ("testFirstName", "testSurname")
    }
  }

  def getOfficerAppointment(officerAppointmentUrl: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    import play.api.http.Status.NO_CONTENT

    val (http, extraHeaders, url) = if (useProxy) {
      (httpProxy, createAPIAuthHeader(), s"$cohoPublicUrl$officerAppointmentUrl")
    } else {
      val (fName, lName) = getStubbedFirstAndLastName(officerAppointmentUrl)
      (httpNoProxy, Seq(), s"$cohoStubbedUrl/get-officer-appointment?fn=$fName&sn=$lName")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter), Some(metrics.publicAPITimer.time())) {
      http.GET[HttpResponse](url = url, headers = extraHeaders).map {
        res =>
          res.status match {
            case NO_CONTENT =>
              logger.info(s"[getOfficerAppointments] - Could not find officer appointment for Officer url  - $officerAppointmentUrl")
              throw new NotFoundException(s"No content for Officer url  - $officerAppointmentUrl")
            case _ => res.json
          }

      }
    } recover handleOfficerAppointmentsError(url)
  }

  private def handleGetCompanyProfileError(crn: String, isScrs: Boolean): PartialFunction[Throwable, Option[JsValue]] = {
    case _: NotFoundException =>
      if (isScrs) {
        pagerduty(PagerDutyKeys.COHO_PUBLIC_API_NOT_FOUND, Some(s"Could not find company data for CRN - $crn"))
        None
      } else {
        logger.info(s"[getCompanyProfile] Could not find company data for CRN - $crn")
        None
      }
    case ex: UpstreamErrorResponse if UpstreamErrorResponse.Upstream4xxResponse.unapply(ex).isDefined =>
      pagerduty(PagerDutyKeys.COHO_PUBLIC_API_4XX, Some(s"Returned status code: ${ex.statusCode} for $crn - reason: ${ex.getMessage}"))
      None
    case _: GatewayTimeoutException =>
      pagerduty(PagerDutyKeys.COHO_PUBLIC_API_GATEWAY_TIMEOUT, Some(s"Gateway timeout for $crn"))
      None
    case _: ServiceUnavailableException =>
      pagerduty(PagerDutyKeys.COHO_PUBLIC_API_SERVICE_UNAVAILABLE)
      None
    case ex: UpstreamErrorResponse if UpstreamErrorResponse.Upstream5xxResponse.unapply(ex).isDefined =>
      pagerduty(PagerDutyKeys.COHO_PUBLIC_API_5XX, Some(s"Returned status code: ${ex.statusCode} for $crn - reason: ${ex.getMessage}"))
      None
    case ex: Throwable =>
      logger.info(s"[getCompanyProfile] - Failed for $crn - reason: ${ex.getMessage}")
      None
  }

  private def handleGetOfficerListError(crn: String): PartialFunction[Throwable, Option[JsValue]] = {
    case _: NotFoundException =>
      logger.info(s"[getOfficerList] - Could not find officer list for CRN - $crn")
      None
    case ex: HttpException =>
      logger.info(s"[getOfficerList] - Returned status code: ${ex.responseCode} for $crn - reason: ${ex.getMessage}")
      None
    case ex: Throwable =>
      logger.info(s"[getOfficerList] - Failed for $crn - reason: ${ex.getMessage}")
      None
  }

  private def handleOfficerAppointmentsError(officerId: String): PartialFunction[Throwable, JsValue] = {
    case ex: NotFoundException =>
      logger.info(s"[getOfficerAppointments] - Could not find officer appointment for Officer ID  - $officerId")
      throw ex
    case ex: HttpException =>
      logger.info(s"[getOfficerAppointments] - Returned status code: ${ex.responseCode} for $officerId - reason: ${ex.getMessage}")
      throw ex
    case ex: Throwable =>
      logger.info(s"[getOfficerAppointments] - Failed for $officerId - reason: ${ex.getMessage}")
      throw ex
  }

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def createAPIAuthHeader(isScrs: Boolean = true): Seq[(String, String)] = {
    val encodedToken = if (isScrs) {
      Base64.encode(cohoPublicApiAuthToken)
    } else {
      Base64.encode(nonSCRSPublicApiAuthToken)
    }
    Seq("Authorization" -> s"Basic $encodedToken")
  }
}