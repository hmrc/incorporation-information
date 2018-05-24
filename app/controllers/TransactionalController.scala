/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.Inject

import config.MicroserviceConfig
import connectors.PublicCohoApiConnector
import models.IncorpUpdate
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Action
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository}
import services.{TransactionalService, TransactionalServiceException}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global


class TransactionalControllerImpl @Inject()(config: MicroserviceConfig,
                                            val publicApiConnector: PublicCohoApiConnector,
                                            val incorpMongo: IncorpUpdateMongo,
                                            val service: TransactionalService) extends TransactionalController {
  lazy val incorpRepo = incorpMongo.repo
  override val whitelistedServices = config.knownSCRSServices.split(",").toSet
}

trait TransactionalController extends BaseController {

  protected val service: TransactionalService
  val publicApiConnector: PublicCohoApiConnector
  val incorpRepo: IncorpUpdateRepository
  val whitelistedServices: Set[String]

  def fetchCompanyProfile(transactionId: String) = Action.async {
    implicit request =>
      service.fetchCompanyProfile(transactionId).map {
        case Some(json) => Ok(json)
        case _ => NotFound
      }
  }

  def fetchIncorporatedCompanyProfile(crn: String) = Action.async {
    implicit request =>
      val callingService = request.headers.get("User-Agent").getOrElse("unknown")
      val isScrs = whitelistedServices(callingService)

      publicApiConnector.getCompanyProfile(crn, isScrs)(hc) map {
        case Some(json) => Ok(json)
        case _ => NotFound
      }
  }

  def fetchOfficerList(transactionId: String) = Action.async {
    implicit request =>
      service.fetchOfficerList(transactionId).map (Ok(_)) recover {
        case ex: TransactionalServiceException =>
          Logger.error(s"[TransactionalController] [fetchOfficerList] - TransactionalServiceException caught - reason: ${ex.message}", ex)
          NotFound
        case ex: Throwable =>
          Logger.error(s"[TransactionalController] [fetchOfficerList] - Exception caught - reason: ${ex.getMessage}", ex)
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
}

