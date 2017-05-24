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
import play.api.Logger
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository}
import uk.gov.hmrc.play.http.HeaderCarrier
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransactionalServiceImpl @Inject()(val connector: IncorporationAPIConnector,
                                         val incorpMongo: IncorpUpdateMongo,
                                         val publicCohoConnector: PublicCohoApiConnector) extends TransactionalService {

  lazy val incorpRepo = incorpMongo.repo
}

trait TransactionalService {

  protected val connector: IncorporationAPIConnector
  val incorpRepo: IncorpUpdateRepository
  val publicCohoConnector: PublicCohoApiConnector

  def fetchCompanyProfile(transactionId:String)(implicit hc:HeaderCarrier):Future[Option[JsValue]] = {
    checkIfCompIncorporated(transactionId) flatMap {
      case Some(crn) => fetchCompanyProfileFromCoho(crn,transactionId)
      case None => fetchCompanyProfileFromTx(transactionId)(hc)
    }
  }

  def fetchOfficerList(transactionId: String)(implicit hc: HeaderCarrier) = {
    connector.fetchTransactionalData(transactionId) map {
      case SuccessfulTransactionalAPIResponse(js) => (js \ "officers").toOption
      case _ => None
    }
  }

  private[services] def fetchCompanyProfileFromTx(transactionId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    val transformer = (JsPath \ "officers").json.prune
    extractJson(connector.fetchTransactionalData(transactionId), transformer)
  }

  private[services] def fetchCompanyProfileFromCoho(crn:String, transactionId:String)(implicit hc:HeaderCarrier):Future[Option[JsValue]] = {
    publicCohoConnector.getCompanyProfile(crn)(hc) flatMap  {
      case Some(s) => Future.successful(transformDataFromCoho(s.as[JsObject]))
      case _ =>
        Logger.info(s"[TransactionalService][fetchCompanyProfileFromCoho] Service failed to fetch a company that appeared incorporated in INCORPORATION_INFORMATION with the crn number: $crn")
        fetchCompanyProfileFromTx(transactionId)
    }
  }

  private[services] def fetchOfficerListFromPublicAPI(crn: String)(implicit hc: HeaderCarrier): Future[Seq[Option[JsObject]]] = {
    publicCohoConnector.getOfficerList(crn) flatMap {
      case Some(js) =>
        val listOfOfficers = (js \ "items").as[Seq[JsObject]]
        Future.sequence(listOfOfficers map { officer =>
          val appointmentUrl = (officer \ "links" \ "officer" \ "appointments").as[String]
          fetchOfficerAppointment(appointmentUrl) map { _ flatMap { oAppointment =>
            val namedElements = transformOfficerAppointment(oAppointment)
            val fullOfficerJson = namedElements.flatMap(nE => transformOfficerList(officer).map(_.as[JsObject] ++ nE.as[JsObject]))
            //todo: transform rest of officer list here
            fullOfficerJson
          }}
        })
      //todo: transform the officers list into what you need and return a Seq(JsValue)
      //todo: then map that and fetch the appointments from the appointment url
      //todo: then drop the original name and append the named elements as \jsObject
      case None =>
        Logger.info(s"[TransactionalService][fetchCompanyProfileFromCoho] Service failed to fetch a company that appeared incorporated in INCORPORATION_INFORMATION with the crn number: $crn")
        Future.successful(Seq(None)) //todo: call fetchOfficerListFromTXAPI here and flatMap top-level map as well as wrapping the right hand value of the Some(js) in a future
    }
  }

  private[services] def fetchOfficerAppointment(url: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    publicCohoConnector.getOfficerAppointment(url) map {
      case Some(js) => transformOfficerAppointment(js)
      case None => None
    }
  }

  private[services] def transformOfficerAppointment(json: JsValue): Option[JsValue] = {
    val reads = (__ \\ "name_elements").json.pick
    json.transform(reads) match {
      case JsSuccess(js, _) => Some(js)
      case _ => None
    }
  }

  private[services] def transformOfficerList(json: JsValue): Option[JsValue] = {
    import scala.language.postfixOps

    def extractAndFold(json: JsObject, name: String): JsObject = {
      (json \ "address" \ name).asOpt[String].fold(Json.obj())(s => Json.obj("address" -> Json.obj(name -> s)))
    }

    val reads: Reads[JsObject] = (
      (__ \ "date_of_birth").json.pickBranch and
      (__ \ "address").json.pickBranch.map{ addr =>
        Json.obj("address" -> Json.obj(
          "address_line_1" -> (addr \ "address" \ "address_line_1").as[String],
          "country" -> (addr \ "address" \ "country").as[String],
          "locality" -> (addr \ "address" \"locality").as[String]
        )).deepMerge(extractAndFold(addr, "address_line_2"))
          .deepMerge(extractAndFold(addr, "premises"))
          .deepMerge(extractAndFold(addr, "postal_code"))
      }
    ) reduce

    json.transform(reads) match {
      case JsSuccess(js, _) => Some(js)
      case _ => None
    }
  }

  private[services] def transformDataFromCoho(js: JsObject):Option[JsValue] ={
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

  private[services] def sicCodesConverter(sicCodes:Option[JsValue]):Option[List[JsObject]] = {
    sicCodes match {
      case Some(_) => Some(sicCodes.get.as[List[String]]
        .map(sicCode => Json.obj("sic_code" -> sicCode, "sic_description" -> "")))
      case None => None
    }
  }

  private[services] def checkIfCompIncorporated(transactionId:String): Future[Option[String]] = {
    incorpRepo.getIncorpUpdate(transactionId) map {
      case Some(s) if s.crn.isDefined => s.crn
      case _ => None
      }
    }


  private[services] def extractJson(f: => Future[TransactionalAPIResponse], transformer: Reads[JsObject]) = {
    f.map {
      case SuccessfulTransactionalAPIResponse(json) => json.transform(transformer) match {
        case JsSuccess(js, p) if !p.toString().contains("unmatched") => Some(js)
        case _ => None
      }
      case FailedTransactionalAPIResponse => None
    }
  }
}
