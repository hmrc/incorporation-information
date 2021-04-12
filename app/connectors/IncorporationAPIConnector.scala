/*
 * Copyright 2021 HM Revenue & Customs
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
import javax.inject.Inject
import models.IncorpUpdate
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsValue, Reads, __}
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.{AlertLogging, DateCalculators, PagerDutyKeys, SCRSFeatureSwitches}

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

class IncorporationAPIConnectorImpl @Inject()(config: MicroserviceConfig,
                                              injMetricsService: MetricsService,
                                              val dateCalculators:DateCalculators,
                                              val httpNoProxy: HttpClient,
                                              val httpProxy: WSHttpProxy) extends IncorporationAPIConnector {
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
      case true => (httpProxy, createAPIAuthHeader, s"$cohoBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter)) {
      http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc, implicitly) map {
        res =>
          res.status match {
            case NO_CONTENT => Seq()
            case _ =>
              val items = res.json.as[IncorpUpdatesResponse].items
              if(
                items exists {
                  case IncorpUpdate(_, "accepted", Some(crn), Some(incorpDate), _, _) => false
                  case IncorpUpdate(_, "rejected", _, _, _, _)                        => false
                  case failure =>
                    Logger.error("CH_UPDATE_INVALID")
                    Logger.info(s"CH Update failed for transaction ID: ${failure.transactionId}. Status: ${failure.status}, incorpdate provided: ${failure.incorpDate.isDefined}")
                    true
                }
              ){ Seq() } else { items }
          }
      }
    } recover handleError(timepoint)
  }

  def checkForIndividualIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, createAPIAuthHeader, s"$cohoBaseUrl/submissions${buildQueryString(timepoint, "1")}")
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
      case true => (httpProxy, createAPIAuthHeader, s"$cohoBaseUrl/submissionData/$transactionID")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/fetch-data/$transactionID")
    }

    metrics.processDataResponseWithMetrics(Some(successCounter), Some(failureCounter), Some(metrics.internalAPITimer.time())) {
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
      pagerduty(PagerDutyKeys.COHO_TX_API_NOT_FOUND, Some(s"Could not find incorporation data for transaction ID - $transactionID"))
      FailedTransactionalAPIResponse
    case ex: Upstream4xxResponse =>
      pagerduty(PagerDutyKeys.COHO_TX_API_4XX,Some(s"${ex.upstreamResponseCode} returned for transaction id - $transactionID"))
      FailedTransactionalAPIResponse
    case _: GatewayTimeoutException =>
      pagerduty(PagerDutyKeys.COHO_TX_API_GATEWAY_TIMEOUT,Some(s"Gateway timeout for transaction id - $transactionID"))
      FailedTransactionalAPIResponse
    case _: ServiceUnavailableException =>
      pagerduty(PagerDutyKeys.COHO_TX_API_SERVICE_UNAVAILABLE)
      FailedTransactionalAPIResponse
    case ex: Upstream5xxResponse =>
      pagerduty(PagerDutyKeys.COHO_TX_API_5XX,Some(s"Returned status code: ${ex.upstreamResponseCode} for $transactionID - reason: ${ex.getMessage}"))
      FailedTransactionalAPIResponse
    case ex: Throwable =>
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Failed for $transactionID - reason: ${ex.getMessage}")
      FailedTransactionalAPIResponse
  }

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def createAPIAuthHeader : HeaderCarrier = {
    HeaderCarrier(authorization = Some(Authorization(s"Bearer $cohoApiAuthToken")))

  }

  private[connectors] def buildQueryString(timepoint: Option[String], itemsPerPage: String = "1") = {
    timepoint match {
      case Some(tp) => s"?timepoint=$tp&items_per_page=$itemsPerPage"
      case _ => s"?items_per_page=$itemsPerPage"
    }
  }
}