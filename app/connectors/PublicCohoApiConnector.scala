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
import connectors.httpParsers.PublicCohoAPIHttpParsers
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.http.{HttpClient, _}
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

trait PublicCohoApiConnector extends BaseConnector with PublicCohoAPIHttpParsers with AlertLogging {

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

  def getCompanyProfile(crn: String, isScrs: Boolean = true)(implicit hc: HeaderCarrier): Future[Option[JsValue]] =
    withRecovery[Option[JsValue]](Some(None))("getCompanyProfile", crn = Some(crn)) {
      withMetrics {
        val (http, extraHeaders, url) = getCompanyProfileHttpMeta(crn, isScrs)
        http.GET[Option[JsValue]](url = url, headers = extraHeaders)(getCompanyProfileHttpReads(crn, isScrs), hc, ec)
      }
    }

  def getOfficerList(crn: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] =
    withRecovery[Option[JsValue]](Some(None))("getOfficerList", crn = Some(crn)) {
      withMetrics {
        val (http, extraHeaders, url) = getOfficerListHttpMeta(crn)
        http.GET[Option[JsValue]](url = url, headers = extraHeaders)(getOfficerListHttpReads(crn), hc, ec)
      }
    }

  def getOfficerAppointment(officerAppointmentUrl: String)(implicit hc: HeaderCarrier): Future[JsValue] =
    withRecovery()("getOfficerAppointment") {
      withMetrics {
        val (http, extraHeaders, url) = getOfficerAppointmentHttpMeta(officerAppointmentUrl)
        http.GET[JsValue](url = url, headers = extraHeaders)(getOfficerAppointmentHttpReads(officerAppointmentUrl), hc, ec)
      }
    }

  private[connectors] def getOfficerListHttpMeta(crn: String): (CoreGet, Seq[(String, String)], String) =
    if (useProxy) {
      (httpProxy, createAPIAuthHeader(), s"$cohoPublicUrl/company/$crn/officers")
    } else {
      (httpNoProxy, Seq(), s"$cohoStubbedUrl/company/$crn/officers")
    }

  private[connectors] def getCompanyProfileHttpMeta(crn: String, isScrs: Boolean): (CoreGet, Seq[(String, String)], String) =
    if (useProxy) {
      (httpProxy, createAPIAuthHeader(isScrs), s"$cohoPublicUrl/company/$crn")
    } else {
      (httpNoProxy, Seq(), s"$cohoStubbedUrl/company-profile/$crn")
    }

  private[connectors] def getOfficerAppointmentHttpMeta(officerAppointmentUrl: String): (CoreGet, Seq[(String, String)], String) =
    if (useProxy) {
      (httpProxy, createAPIAuthHeader(), s"$cohoPublicUrl$officerAppointmentUrl")
    } else {
      val (fName, lName) = getStubbedFirstAndLastName(officerAppointmentUrl)
      (httpNoProxy, Seq(), s"$cohoStubbedUrl/get-officer-appointment?fn=$fName&sn=$lName")
    }

  private[connectors] def getStubbedFirstAndLastName(url: String): (String, String) = {
    val nUrl = url.replace("appointments", "").replaceAll("[`*{}\\[\\]()>#+:~'%^&@<?;,\"!$=|./_]", "")
    nUrl.length match {
      case x if x > 15 => (nUrl.takeRight(15), nUrl.take(15))
      case _ => ("testFirstName", "testSurname")
    }
  }

  private def withMetrics[T](f: => Future[T]): Future[T] =
    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter), Some(metrics.publicAPITimer.time()))(f)

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