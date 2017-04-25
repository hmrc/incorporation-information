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

import config.{MicroserviceConfig, WSHttp, WSHttpProxy}
import models.IncorpUpdate
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import play.api.libs.json.{JsValue, Reads, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.SCRSFeatureSwitches

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
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
class IncorporationAPIConnectorImpl @Inject()(config: MicroserviceConfig) extends IncorporationAPIConnector {
  val stubBaseUrl = config.incorpFrontendStubUrl
  val cohoBaseUrl = config.companiesHouseUrl
  val cohoApiAuthToken = config.incorpUpdateCohoApiAuthToken
  val itemsToFetch = config.incorpUpdateItemsToFetch
  val httpNoProxy = WSHttp
  val httpProxy = WSHttpProxy
  val featureSwitch = SCRSFeatureSwitches
}

case class IncorpUpdateAPIFailure(ex: Exception) extends NoStackTrace

sealed trait TransactionalAPIResponse
case class SuccessfulTransactionalAPIResponse(js: JsValue) extends TransactionalAPIResponse
case object FailedTransactionalAPIResponse extends TransactionalAPIResponse

trait IncorporationAPIConnector {

  def httpProxy: HttpGet with WSProxy
  def httpNoProxy: HttpGet

  val featureSwitch: SCRSFeatureSwitches
  val stubBaseUrl: String
  val cohoBaseUrl: String
  val cohoApiAuthToken: String
  val itemsToFetch: String

  def checkForIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    import play.api.http.Status.NO_CONTENT

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoBaseUrl/submissions${buildQueryString(timepoint, itemsToFetch)}")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/incorporation-frontend-stubs/submissions${buildQueryString(timepoint, itemsToFetch)}")
    }

    http.GET[HttpResponse](url)(implicitly[HttpReads[HttpResponse]], realHc) map {
      res =>
        res.status match {
          case NO_CONTENT => Seq()
          case _ => res.json.as[IncorpUpdatesResponse].items
        }
    } recover handleError(timepoint)
  }

  def fetchTransactionalData(transactionID: String)(implicit hc: HeaderCarrier): Future[TransactionalAPIResponse] = {
    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc), s"$cohoBaseUrl/submissionData/$transactionID")
      case false => (httpNoProxy, hc, s"$stubBaseUrl/incorporation-frontend-stubs/fetch-data/$transactionID")
    }

    // TODO - change from warn when logger starts logging info in env's
    Logger.warn(s"[TransactionalConnector] [fetchTransactionalData] - url : $url - auth token : ${realHc.authorization}")

    http.GET[JsValue](url)(implicitly[HttpReads[JsValue]], realHc) map { res =>
      Logger.warn("json - " + res)
      SuccessfulTransactionalAPIResponse(res)
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
      Logger.error("[IncorporationCheckAPIConnector] [incorpUpdates]" + ex.upstreamResponseCode + " " + ex.message)
      throw new IncorpUpdateAPIFailure(ex)
    case ex: Upstream5xxResponse =>
      Logger.error("[IncorporationCheckAPIConnector] [incorpUpdates]" + ex.upstreamResponseCode + " " + ex.message)
      throw new IncorpUpdateAPIFailure(ex)
    case ex: Exception =>
      Logger.error("[IncorporationCheckAPIConnector] [incorpUpdates]" + ex)
      throw new IncorpUpdateAPIFailure(ex)
  }

  private def handleError(transactionID: String): PartialFunction[Throwable, TransactionalAPIResponse] = {
    case ex: NotFoundException =>
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Could not find incorporation data for transaction ID - $transactionID")
      FailedTransactionalAPIResponse
    case ex: HttpException =>
      Logger.info(s"[TransactionalConnector] [fetchTransactionalData] - Returned status code: ${ex.responseCode} for $transactionID - reason: ${ex.getMessage}")
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
