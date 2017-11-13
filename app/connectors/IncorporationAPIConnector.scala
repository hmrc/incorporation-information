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

import javax.inject.{Inject, Singleton}

import com.codahale.metrics.Counter
import config.{MicroserviceConfig, WSHttp, WSHttpProxy}
import models.IncorpUpdate
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsValue, Reads, __}
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.{AlertLogging, SCRSFeatureSwitches}

import scala.concurrent.Future
import scala.util.control.NoStackTrace

case class IncorpUpdatesResponse(items: Seq[IncorpUpdate], nextLink: String)
object IncorpUpdatesResponse {
  val dateReads = Reads[DateTime]( js =>
    js.validate[String].map[DateTime](DateTime.parse(_, DateTimeFormat.forPattern("yyyy-MM-dd")))
  )

  implicit val updateFmt = IncorpUpdate.cohoFormat

  implicit val reads : Reads[IncorpUpdatesResponse] = (
    ( __ \ "items" ).read[Seq[IncorpUpdate]] and
      (__ \ "links" \ "next").read[String]
    )(IncorpUpdatesResponse.apply _)

}

@Singleton
class IncorporationAPIConnectorImpl @Inject()(config: MicroserviceConfig,
                                              injMetricsService: MetricsService) extends IncorporationAPIConnector {
  val stubBaseUrl = config.incorpFrontendStubUrl
  val cohoBaseUrl = config.companiesHouseUrl
  val cohoApiAuthToken = config.incorpUpdateCohoApiAuthToken
  val itemsToFetch = config.incorpUpdateItemsToFetch
  val httpNoProxy = WSHttp
  val httpProxy = WSHttpProxy
  val featureSwitch = SCRSFeatureSwitches
  override val metrics: MetricsService = injMetricsService
  override lazy val successCounter: Counter = metrics.transactionApiSuccessCounter
  override lazy val failureCounter: Counter = metrics.transactionApiFailureCounter

  protected val loggingDays: String = config.noRegisterAnInterestLoggingDay
  protected val loggingTimes: String = config.noRegisterAnInterestLoggingTime
}

case class IncorpUpdateAPIFailure(ex: Exception) extends NoStackTrace

sealed trait TransactionalAPIResponse
case class SuccessfulTransactionalAPIResponse(js: JsValue) extends TransactionalAPIResponse
case object FailedTransactionalAPIResponse extends TransactionalAPIResponse

trait IncorporationAPIConnector extends AlertLogging {

  def httpProxy: CoreGet with WSProxy
  def httpNoProxy: CoreGet

  val featureSwitch: SCRSFeatureSwitches
  val stubBaseUrl: String
  val cohoBaseUrl: String
  val cohoApiAuthToken: String
  val itemsToFetch: String
  val metrics: MetricsService
  val successCounter: Counter
  val failureCounter: Counter

  def checkForIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter)) {
      http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc, implicitly) map {
        res =>
          res.status match {
            case NO_CONTENT => Seq()
            case _ => res.json.as[IncorpUpdatesResponse].items
          }
      }
    } recover handleError(timepoint)
  }

  def checkForIndividualIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoBaseUrl/submissions${buildQueryString(timepoint, "1")}")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/submissions${buildQueryString(timepoint, "1")}")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter)) {
      http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc, implicitly) map {
        res =>
          res.status match {
            case NO_CONTENT => Seq()
            case _ => res.json.as[IncorpUpdatesResponse].items
          }
      }
    } recover handleError(timepoint)
  }


  def fetchTransactionalData(transactionID: String)(implicit hc: HeaderCarrier): Future[TransactionalAPIResponse] = {
    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoBaseUrl/submissionData/$transactionID")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/fetch-data/$transactionID")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter)) {
      http.GET[JsValue](url)(implicitly[HttpReads[JsValue]], realHc, implicitly) map { res =>
        Logger.debug("[TransactionalData] json - " + res)
        SuccessfulTransactionalAPIResponse(res)
      }
    } recover handleError(transactionID)
  }

  private def logError(ex: HttpException, timepoint: Option[String]) = {
    Logger.error(s"[IncorporationCheckAPIConnector] [incorpUpdates]" +
      s" request to fetch incorp updates returned a ${ex.responseCode}. " +
      s"No incorporations were processed for timepoint $timepoint - Reason = ${ex.getMessage}")
  }

  private def handleError(timepoint: Option[String]): PartialFunction[Throwable, Seq[IncorpUpdate]] = {
    case ex: BadRequestException =>
      logError(ex, timepoint)
      throw new IncorpUpdateAPIFailure(ex)
    case ex: NotFoundException =>
      logError(ex, timepoint)
      throw new IncorpUpdateAPIFailure(ex)
    case ex: Upstream4xxResponse =>
      Logger.error("[IncorporationCheckAPIConnector] [checkForIncorpUpdate]" + ex.upstreamResponseCode + " " + ex.message)
      throw new IncorpUpdateAPIFailure(ex)
    case ex: Upstream5xxResponse =>
      Logger.error("[IncorporationCheckAPIConnector] [checkForIncorpUpdate]" + ex.upstreamResponseCode + " " + ex.message)
      throw new IncorpUpdateAPIFailure(ex)
    case ex: Exception =>
      Logger.error("[IncorporationCheckAPIConnector] [checkForIncorpUpdate]" + ex)
      throw new IncorpUpdateAPIFailure(ex)
  }

  private def handleError(transactionID: String): PartialFunction[Throwable, TransactionalAPIResponse] = {
    case _: NotFoundException =>
      alertCohoTxAPINotFound()
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Could not find incorporation data for transaction ID - $transactionID")
      FailedTransactionalAPIResponse
    case ex: Upstream4xxResponse =>
      alertCohoTxAPI4xx()
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - ${ex.upstreamResponseCode} returned for transaction id - $transactionID")
      FailedTransactionalAPIResponse
    case _: GatewayTimeoutException =>
      alertCohoTxAPIGatewayTimeout()
      Logger.error(s"[TransactionalConnector] [fetchTransactionalData] - Gateway timeout for transaction id - $transactionID")
      FailedTransactionalAPIResponse
    case _: ServiceUnavailableException =>
      alertCohoTxAPIServiceUnavailable()
      FailedTransactionalAPIResponse
    case ex: Upstream5xxResponse =>
      alertCohoTxAPI5xx()
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Returned status code: ${ex.upstreamResponseCode} for $transactionID - reason: ${ex.getMessage}")
      FailedTransactionalAPIResponse
    case ex: Throwable =>
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Failed for $transactionID - reason: ${ex.getMessage}")
      FailedTransactionalAPIResponse
  }

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def appendAPIAuthHeader(hc: HeaderCarrier): HeaderCarrier = {
    hc.copy(authorization = Some(Authorization(s"Bearer $cohoApiAuthToken")))
  }

  private[connectors] def buildQueryString(timepoint: Option[String], itemsPerPage: String = "1") = {
    timepoint match {
      case Some(tp) => s"?timepoint=$tp&items_per_page=$itemsPerPage"
      case _ => s"?items_per_page=$itemsPerPage"
    }
  }
}
