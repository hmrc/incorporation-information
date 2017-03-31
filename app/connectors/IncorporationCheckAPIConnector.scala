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
import play.api.libs.json.{Reads, __}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.SCRSFeatureSwitches

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
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
class IncorporationCheckAPIConnectorImpl @Inject()(config: MicroserviceConfig) extends IncorporationCheckAPIConnector {
  val cohoAPIStubUrl = config.incorpUpdateStubUrl
  val cohoAPIUrl = config.incorpUpdateCohoAPIUrl
  val cohoApiAuthToken = config.incorpUpdateCohoApiAuthToken
  val itemsToFetch = config.incorpUpdateItemsToFetch
  val httpNoProxy = WSHttp
  val httpProxy = WSHttpProxy
  val featureSwitch = SCRSFeatureSwitches
}

case class IncorpUpdateAPIFailure(ex: Exception) extends NoStackTrace

trait IncorporationCheckAPIConnector {

  val cohoAPIStubUrl: String
  val cohoAPIUrl: String
  val cohoApiAuthToken: String
  val itemsToFetch: String
  val httpNoProxy: HttpGet
  val httpProxy: HttpGet with WSProxy
  val featureSwitch: SCRSFeatureSwitches

  private[connectors] def useProxy: Boolean = featureSwitch.transactionalAPI.enabled

  private[connectors] def buildQueryString(timepoint: Option[String], itemsPerPage: String = "1") = {
    timepoint match {
      case Some(tp) => s"?timepoint=$tp&items_per_page=$itemsPerPage"
      case _ => s"?items_per_page=$itemsPerPage"
    }
  }

  def logError(ex: HttpException, timepoint: Option[String]) = {
    Logger.error(s"[IncorporationCheckAPIConnector] [incorpUpdates]" +
      s" request to fetch incorp updates returned a ${ex.responseCode}. " +
      s"No incorporations were processed for timepoint ${timepoint} - Reason = ${ex.getMessage}")
  }

  private[connectors] def appendAPIAuthHeader(hc: HeaderCarrier, token: String): HeaderCarrier = {
    hc.copy(authorization = Some(Authorization(s"Bearer $token")))
  }

  // TODO - II-INCORP - refactor the recover block - move to a separate method to provide more clarity
  def checkForIncorpUpdate(timepoint: Option[String] = None)(implicit hc: HeaderCarrier): Future[Seq[IncorpUpdate]] = {
    import play.api.http.Status.{NO_CONTENT}

    val queryString = buildQueryString(timepoint, itemsToFetch)

    val (http, realHc, url) = useProxy match {
      case true => (httpProxy, appendAPIAuthHeader(hc, cohoApiAuthToken), s"$cohoAPIUrl$queryString")
      case false => (httpNoProxy, hc, s"$cohoAPIStubUrl$queryString")
    }

    val httpRds = implicitly[HttpReads[HttpResponse]]

    http.GET[HttpResponse](url)(httpRds, realHc) map {
      res =>
        res.status match {
          case NO_CONTENT => Seq()
          case _ => res.json.as[IncorpUpdatesResponse].items
        }
    } recover {
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
  }
}
