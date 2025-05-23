/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers

import config.MicroserviceConfig
import connectors.PublicCohoApiConnector
import models.IncorpUpdate
import utils.Logging
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repositories.{IncorpUpdateMongo, IncorpUpdateMongoRepository, IncorpUpdateRepository}
import services.{TransactionalService, TransactionalServiceException}
import uk.gov.hmrc.play.bootstrap.backend.controller.{BackendBaseController, BackendController}

import javax.inject.Inject
import scala.concurrent.ExecutionContext


class TransactionalControllerImpl @Inject()(config: MicroserviceConfig,
                                            val publicApiConnector: PublicCohoApiConnector,
                                            val incorpMongo: IncorpUpdateMongo,
                                            val service: TransactionalService,
                                            override val controllerComponents: ControllerComponents
                                           )(implicit val ec: ExecutionContext) extends BackendController(controllerComponents) with TransactionalController {
  lazy val incorpRepo: IncorpUpdateMongoRepository = incorpMongo.repo
  override val allowlistedServices: Set[String] = config.knownSCRSServices.split(",").toSet
}

trait TransactionalController extends BackendBaseController with Logging {

  implicit val ec: ExecutionContext
  protected val service: TransactionalService
  val publicApiConnector: PublicCohoApiConnector
  val incorpRepo: IncorpUpdateRepository
  val allowlistedServices: Set[String]

  def fetchCompanyProfile(transactionId: String): Action[AnyContent] = Action.async {
    implicit request =>
      service.fetchCompanyProfile(transactionId).map {
        case Some(json) => Ok(json)
        case _ => NotFound
      }
  }

  def fetchShareholders(transactionId: String): Action[AnyContent] = Action.async {
    implicit request =>
      service.fetchShareholders(transactionId).map {
        case Some(json) if json.value.nonEmpty => Ok(json)
        case Some(_) => NoContent
        case _ => NotFound
      }
  }

  def fetchIncorporatedCompanyProfile(crn: String): Action[AnyContent] = Action.async {
    implicit request =>
      val callingService = request.headers.get("User-Agent").getOrElse("unknown")
      val isScrs = allowlistedServices(callingService)

      publicApiConnector.getCompanyProfile(crn, isScrs)(hc) map {
        case Some(json) => Ok(json)
        case _ => NotFound
      }
  }

  def fetchOfficerList(transactionId: String): Action[AnyContent] = Action.async {
    implicit request =>
      service.fetchOfficerList(transactionId).map { json =>
        val officerArray = (json \ "officers").as[JsArray]
        officerArray match {
          case _ if (officerArray.value.isEmpty) =>
            logger.warn(s"[fetchOfficerList] - Officer list was empty (this should not happen as this means no name elements are present this should be investigated)")
            NoContent
          case _ => Ok(json)
        }
      } recover {
        case _: TransactionalServiceException =>
          logger.error(s"[fetchOfficerList] - TransactionalServiceException caught - Officer List not found")
          NotFound
        case _: Throwable =>
          logger.error(s"[fetchOfficerList] - TransactionalServiceException caught - Failed to fetch Officer List internal server error")
          InternalServerError
      }
  }

  def fetchIncorpUpdate(transactionId: String): Action[AnyContent] = Action.async {
      incorpRepo.getIncorpUpdate(transactionId) map {
        case Some(incorpUpdate) => Ok(Json.toJson(incorpUpdate)(IncorpUpdate.responseFormat))
        case _ => NoContent
      }
  }

  def fetchSicCodesByTransactionID(txId: String): Action[AnyContent] = Action.async {
    implicit request =>
      service.fetchSicByTransId(txId) map {
        case Some(sicCodes) => Ok(sicCodes)
        case _ => NoContent
      }
  }

  def fetchSicCodesByCRN(crn: String): Action[AnyContent] = Action.async {
    implicit request =>
      service.fetchSicByCRN(crn) map {
        case Some(json) => Ok(json)
        case None => NoContent
      }
  }
}