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

import com.codahale.metrics.{Counter, Timer}
import config.{MicroserviceConfig, WSHttpProxy}
import connectors.httpParsers.IncorporationAPIHttpParsers
import models.{IncorpUpdate, IncorpUpdatesResponse}
import play.api.libs.json.JsValue
import services.MetricsService
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.play.http.ws.WSProxy
import utils._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class IncorporationAPIConnectorImpl @Inject()(config: MicroserviceConfig,
                                              injMetricsService: MetricsService,
                                              val dateCalculators: DateCalculators,
                                              val httpNoProxy: HttpClient,
                                              val httpProxy: WSHttpProxy
                                             )(implicit val ec: ExecutionContext) extends IncorporationAPIConnector {
  lazy val stubBaseUrl = config.incorpFrontendStubUrl
  lazy val cohoBaseUrl = config.companiesHouseUrl
  lazy val cohoApiAuthToken = config.incorpUpdateCohoApiAuthToken
  lazy val itemsToFetch = config.incorpUpdateItemsToFetch
  lazy val featureSwitch = SCRSFeatureSwitches
  override lazy val metrics: MetricsService = injMetricsService
  override lazy val successCounter: Counter = metrics.transactionApiSuccessCounter
  override lazy val failureCounter: Counter = metrics.transactionApiFailureCounter

  protected lazy val loggingDays: String = config.noRegisterAnInterestLoggingDay
  protected lazy val loggingTimes: String = config.noRegisterAnInterestLoggingTime
}

case class IncorpUpdateAPIFailure(ex: Exception) extends Exception with NoStackTrace

sealed trait TransactionalAPIResponse

case class SuccessfulTransactionalAPIResponse(js: JsValue) extends TransactionalAPIResponse

case object FailedTransactionalAPIResponse extends TransactionalAPIResponse

trait IncorporationAPIConnector extends BaseConnector with IncorporationAPIHttpParsers with AlertLogging {

  def httpProxy: CoreGet with WSProxy

  def httpNoProxy: CoreGet

  implicit val ec: ExecutionContext
  val featureSwitch: SCRSFeatureSwitches
  val stubBaseUrl: String
  val cohoBaseUrl: String
  val cohoApiAuthToken: String
  val itemsToFetch: String
  val metrics: MetricsService
  val successCounter: Counter
  val failureCounter: Counter

  def checkForIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] =
    withIncorpUpdateAPIRecovery {
      withMetrics() {
        val (http, extraHeaders, url) = incorpUpdateHttpMeta(timepoint, itemsToFetch)
        http.GET[Seq[IncorpUpdate]](url = url, headers = extraHeaders)(checkForIncorpUpdateHttpReads(timepoint), hc, ec)
      }
    }

  def checkForIndividualIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] =
    withIncorpUpdateAPIRecovery {
      withMetrics() {
        val (http, extraHeaders, url) = incorpUpdateHttpMeta(timepoint, "1")
        http.GET[Seq[IncorpUpdate]](url = url, headers = extraHeaders)(checkForIndividualIncorpUpdateHttpReads(timepoint), hc, ec)
      }
    }

  def fetchTransactionalData(transactionID: String)(implicit hc: HeaderCarrier): Future[TransactionalAPIResponse] =
    withRecovery[TransactionalAPIResponse](Some(FailedTransactionalAPIResponse))("fetchTransactionalData", txId = Some(transactionID)) {
      withMetrics(Some(metrics.internalAPITimer.time())) {
        val (http, extraHeaders, url) = fetchTransactionalHttpMeta(transactionID)
        http.GET[TransactionalAPIResponse](url = url, headers = extraHeaders)(fetchTransactionalDataHttpReads(transactionID), hc, ec)
      }
    }

  private def withMetrics[T](timer: Option[Timer.Context] = None)(f: => Future[T]): Future[T] =
    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter), timer)(f)

  private def withIncorpUpdateAPIRecovery(f: => Future[Seq[IncorpUpdate]]): Future[Seq[IncorpUpdate]] = f recover {
    case e: IncorpUpdateAPIFailure => throw e
    case e: Exception => throw IncorpUpdateAPIFailure(e)
  }

  private[connectors] def incorpUpdateHttpMeta(timepoint: Option[String], itemsToFetch: String): (CoreGet, Seq[(String, String)], String) =
    if (useProxy) {
      (httpProxy, createAPIAuthHeader, s"$cohoBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
    } else {
      (httpNoProxy, Seq(), s"$stubBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
    }

  private[connectors] def fetchTransactionalHttpMeta(transactionID: String): (CoreGet, Seq[(String, String)], String) =
    if (useProxy) {
      (httpProxy, createAPIAuthHeader, s"$cohoBaseUrl/submissionData/$transactionID")
    } else {
      (httpNoProxy, Seq(), s"$stubBaseUrl/fetch-data/$transactionID")
    }

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def createAPIAuthHeader: Seq[(String, String)] = Seq("Authorization" -> s"Bearer $cohoApiAuthToken")

  private[connectors] def buildQueryString(timepoint: Option[String], itemsPerPage: String = "1") = {
    timepoint match {
      case Some(tp) => s"?timepoint=$tp&items_per_page=$itemsPerPage"
      case _ => s"?items_per_page=$itemsPerPage"
    }
  }
}