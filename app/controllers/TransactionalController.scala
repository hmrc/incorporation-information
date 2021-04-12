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

package controllers

import config.MicroserviceConfig
import connectors.PublicCohoApiConnector
import javax.inject.Inject
import models.IncorpUpdate
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents}
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository}
import services.{TransactionalService, TransactionalServiceException}
import uk.gov.hmrc.play.bootstrap.controller.{BackendBaseController, BackendController, BaseController}

import scala.concurrent.ExecutionContext.Implicits.global


class TransactionalControllerImpl @Inject()(config: MicroserviceConfig,
                                            val publicApiConnector: PublicCohoApiConnector,
                                            val incorpMongo: IncorpUpdateMongo,
                                            val service: TransactionalService,
                                            override val controllerComponents: ControllerComponents)
  extends BackendController(controllerComponents) with TransactionalController {
  lazy val incorpRepo = incorpMongo.repo
  override val allowlistedServices = config.knownSCRSServices.split(",").toSet
}

trait TransactionalController extends BackendBaseController {

  protected val service: TransactionalService
  val publicApiConnector: PublicCohoApiConnector
  val incorpRepo: IncorpUpdateRepository
  val allowlistedServices: Set[String]

  def fetchCompanyProfile(transactionId: String) = Action.async {
    implicit request =>
      service.fetchCompanyProfile(transactionId).map {
        case Some(json) => Ok(json)
        case _ => NotFound
      }
  }

  def fetchShareholders(transactionId: String) = Action.async {
    implicit request =>
      service.fetchShareholders(transactionId).map {
        case Some(json) if json.value.nonEmpty => Ok(json)
        case Some(_) => NoContent
        case _ => NotFound
      }
  }

  def fetchIncorporatedCompanyProfile(crn: String) = Action.async {
    implicit request =>
      val callingService = request.headers.get("User-Agent").getOrElse("unknown")
      val isScrs = allowlistedServices(callingService)

      publicApiConnector.getCompanyProfile(crn, isScrs)(hc) map {
        case Some(json) => Ok(json)
        case _ => NotFound
      }
  }

  def fetchOfficerList(transactionId: String) = Action.async {
    implicit request =>
      service.fetchOfficerList(transactionId).map(Ok(_)) recover {
        case ex: TransactionalServiceException =>
          Logger.error(s"[TransactionalController] [fetchOfficerList] - TransactionalServiceException caught - reason: ${
            ex.message
          }", ex)
          NotFound
        case ex: Throwable =>
          Logger.error(s"[TransactionalController] [fetchOfficerList] - Exception caught - reason: ${
            ex.getMessage
          }", ex)
          InternalServerError
      }
  }

  def fetchIncorpUpdate(transactionId: String) = Action.async {
    implicit request =>
      incorpRepo.getIncorpUpdate(transactionId) map {
        case Some(incorpUpdate) => Ok(Json.toJson(incorpUpdate)(IncorpUpdate.responseFormat))
        case _ => NoContent
      }
  }

  def fetchSicCodesByTransactionID(txId: String) = Action.async {
    implicit request =>
      service.fetchSicByTransId(txId) map {
        case Some(sicCodes) => Ok(sicCodes)
        case _ => NoContent
      }
  }

  def fetchSicCodesByCRN(crn: String) = Action.async {
    implicit request =>
      service.fetchSicByCRN(crn) map {
        case Some(json) => Ok(json)
        case None => NoContent
      }
  }
}