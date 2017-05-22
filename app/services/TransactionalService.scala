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

package services

import javax.inject.Inject

import connectors._
import models.IncorpUpdate
import play.api.Logger
import play.api.libs.json._
import repositories.{IncorpUpdateMongo, IncorpUpdateMongoRepository, IncorpUpdateRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.libs.json.Reads._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransactionalServiceImpl @Inject()(val connector: IncorporationAPIConnector, val incorpMongo: IncorpUpdateMongo, val cohoConnector: PublicCohoApiConnector) extends TransactionalService {

  lazy val incorpRepos = incorpMongo.repo
}

trait TransactionalService {

  protected val connector: IncorporationAPIConnector
  val incorpRepos: IncorpUpdateRepository
  val cohoConnector: PublicCohoApiConnector

  def fetchCompanyProfileFromTx(transactionId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    val transformer = (JsPath \ "officers").json.prune
    extractJson(connector.fetchTransactionalData(transactionId), transformer)
  }
def fetchCompanyProfileFromTxOrCoho(transactionId:String)(implicit hc:HeaderCarrier):Future[Option[JsValue]] = {
  checkIfCompIncorporated(transactionId) flatMap {
    case Some(s) => fetchCompanyProfileFromCoho(s)
    case None => fetchCompanyProfileFromTx(transactionId)(hc)
  }
}
  def fetchCompanyProfileFromCoho(crn:String)(implicit hc:HeaderCarrier):Future[Option[JsValue]] = {
    cohoConnector.getCompanyProfile(crn)(hc) map {
      case Some(s) => transformDataFromCoho(s.as[JsObject])
      case _ => {Logger.info(s"[TransactionalService][fetchCompanyProfileFromCoho] Service failed to fetch a company that appeared incorporated in INCORPORATION_INFORMATION with the crn number: ${crn} ")
        None}
    }
  }
  def transformDataFromCoho(js: JsObject):Option[JsValue] ={
      val companyType = (js \ "type").as[String]
      val companyName = (js \ "company_name").as[String]
      val companyStatus = (js \ "company_status").toOption
      val companyNumber = (js \ "company_number").as[String]
      val sicCodes = (js \ "sic_codes").toOption
      val registeredOffice = (js \ "registered_office_address").as[JsObject]
      val registeredOfficeAddressPruned = (registeredOffice - ("care_of")
      - ("region") - ("po_box"))
      val initialRes = (Json.obj("company_type" -> Json.toJson(companyType))
      + ("type" -> Json.toJson(companyType))
      + ("registered_office_address" -> registeredOfficeAddressPruned)
      + ("company_name" -> Json.toJson(companyName))
      + ("company_number" -> Json.toJson(companyNumber)))
    val res = sicCodesConverter(sicCodes) match {
       case Some(s) => initialRes + ("sic_codes" -> Json.toJson(s))
       case None => initialRes
     }

    val finalJsonResult = companyStatus match {
      case Some(s) => res + ("company_status" -> Json.toJson(s.as[String]))
      case _ => res
    }
    Some(finalJsonResult)
  }

  def sicCodesConverter(sicCodes:Option[JsValue]):Option[List[JsObject]] = {
    sicCodes match {
      case Some(_) => Some(sicCodes.get.as[List[String]]
        .map(sicCode => Json.obj("sic_code" -> sicCode, "sic_description" -> "")))
      case None => None
    }
  }

  def fetchOfficerList(transactionId: String)(implicit hc: HeaderCarrier) = {
    connector.fetchTransactionalData(transactionId) map {
      case SuccessfulTransactionalAPIResponse(js) => (js \ "officers").toOption
      case _ => None
    }
  }

  def checkIfCompIncorporated(transactionId:String): Future[Option[String]] = {
    incorpRepos.getIncorpUpdate(transactionId) map {
      case Some(s) if(s.crn.isDefined) => s.crn
      case _ => None
      }
    }


  private[services] def extractJson(f: => Future[TransactionalAPIResponse], transformer: Reads[JsObject]) = {
    f.map {
      case SuccessfulTransactionalAPIResponse(json) => json.transform(transformer) match {
        case JsSuccess(js, path) if !path.toString().contains("unmatched") => Some(js)
        case _ => None
      }
      case FailedTransactionalAPIResponse => None
    }
  }
}
