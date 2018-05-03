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

package services

import javax.inject.Inject
import connectors._
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import repositories.{IncorpUpdateMongo, IncorpUpdateRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http.HeaderCarrier
import utils.TimestampFormats

sealed trait TransactionalServiceException extends Throwable {
  val message = this.getMessage
}
case class NoItemsFoundException() extends TransactionalServiceException
case class FailedToFetchOfficerListFromTxAPI() extends TransactionalServiceException with NoStackTrace

class TransactionalServiceImpl @Inject()(val connector: IncorporationAPIConnector,
                                         val incorpMongo: IncorpUpdateMongo,
                                         val publicCohoConnector: PublicCohoApiConnector) extends TransactionalService {

  lazy val incorpRepo = incorpMongo.repo
}

trait TransactionalService {

  protected val connector: IncorporationAPIConnector
  val incorpRepo: IncorpUpdateRepository
  val publicCohoConnector: PublicCohoApiConnector

  def fetchCompanyProfile(transactionId:String)(implicit hc:HeaderCarrier): Future[Option[JsValue]] = {
    checkIfCompIncorporated(transactionId) flatMap {
      case Some(crn) => fetchCompanyProfileFromCoho(crn, transactionId)
      case None => fetchCompanyProfileFromTx(transactionId)(hc)
    }
  }

  def fetchOfficerList(transactionId: String)(implicit hc: HeaderCarrier): Future[JsValue] = {
    checkIfCompIncorporated(transactionId) flatMap {
      case Some(crn) => fetchOfficerListFromPublicAPI(transactionId, crn)
      case None => fetchOfficerListFromTxAPI(transactionId)
    }
  }

  private[services] def fetchOfficerListFromTxAPI(transactionID: String)(implicit hc: HeaderCarrier): Future[JsObject] = {
    connector.fetchTransactionalData(transactionID) map {
      case SuccessfulTransactionalAPIResponse(js) => Json.obj("officers" -> (js \ "officers").as[JsArray])
      case _ => throw new FailedToFetchOfficerListFromTxAPI
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

  private[services] def fetchOfficerListFromPublicAPI(transactionId: String, crn: String)(implicit hc: HeaderCarrier): Future[JsObject] = {
    publicCohoConnector.getOfficerList(crn) flatMap {
      case Some(officerList) =>
        val listOfOfficers = (officerList \ "items").as[Seq[JsObject]]
        Future.sequence(listOfOfficers map { officer =>
          val appointmentUrl = (officer \ "links" \ "officer" \ "appointments").as[String]
          fetchOfficerAppointment(appointmentUrl) map { officerAppointment =>
            transformOfficerAppointment(officerAppointment) match {
              case Some(oA) => oA ++ transformOfficerList(officer)
              case _ => transformOfficerList(officer)
            }
          }
        }) map { listTransformedOfficers =>
          Json.obj("officers" -> Json.toJson(listTransformedOfficers).as[JsArray])
        }
      case None =>

        Logger.info(s"[TransactionalService][fetchCompanyProfileFromCoho] Service failed to fetch a company that appeared incorporated in INCORPORATION_INFORMATION with the crn number: $crn")
        fetchOfficerListFromTxAPI(transactionId)
    }
  }

  private[services] def fetchOfficerAppointment(url: String)(implicit hc: HeaderCarrier): Future[JsObject] = {
    publicCohoConnector.getOfficerAppointment(url) map (_.as[JsObject])
  }

  private[services] def transformOfficerAppointment(publicAppointment: JsValue): Option[JsObject] = {
    ((publicAppointment \ "items").head.getOrElse(throw new NoItemsFoundException) \ "name_elements").toOption match {
    case Some(a) => Some(Json.obj("name_elements" -> a.as[JsObject]))
    case _ => None
 }
  }

  private[services] def transformOfficerList(publicOfficerList: JsValue): JsObject = {
    import scala.language.postfixOps

    def extractAndFold(json: JsObject, name: String): JsObject = {
      (json \ "address" \ name).asOpt[String].fold(Json.obj())(s => Json.obj("address" -> Json.obj(name -> s)))
    }

    val reads: Reads[JsObject] = (
      (__ \ "officer_role").read[String].map(role => Json.obj("officer_role" -> role)) and
        (__ \ "resigned_on").readNullable[String].map(date =>
            date.fold(Json.obj())(d => Json.obj("resigned_on" -> d))
        ) and
        (__ \ "links" \ "officer" \ "appointments").json.pick.map{ link =>
            Json.obj("appointment_link" -> link.as[JsString])
        } and
        (__ \ "date_of_birth").readNullable[JsObject].map { oDOB =>
          oDOB.fold(Json.obj())(dob => Json.obj("date_of_birth" -> dob))
        } and
        (__ \ "address").json.pickBranch.map{ addr =>
        Json.obj("address" -> Json.obj(
          "address_line_1" -> (addr \ "address" \ "address_line_1").as[String],
          "locality" -> (addr \ "address" \"locality").as[String]
        )).deepMerge(extractAndFold(addr, "address_line_2"))
          .deepMerge(extractAndFold(addr, "premises"))
          .deepMerge(extractAndFold(addr, "postal_code"))
          .deepMerge(extractAndFold(addr, "country"))
      }
    ) reduce

    publicOfficerList.transform(reads).get
  }

  private[services] def transformDataFromCoho(js: JsObject):Option[JsValue] ={
      val companyType = (js \ "type").as[String]
      val companyName = (js \ "company_name").as[String]
      val companyStatus = (js \ "company_status").toOption
      val companyNumber = (js \ "company_number").as[String]
      val sicCodes = (js \ "sic_codes").toOption
      val registeredOffice = (js \ "registered_office_address").toOption
      val initialRes = (Json.obj("company_type" -> Json.toJson(companyType))
      + ("type" -> Json.toJson(companyType))

      + ("company_name" -> Json.toJson(companyName))
      + ("company_number" -> Json.toJson(companyNumber)))
    val res = sicCodesConverter(sicCodes) match {
       case Some(s) => initialRes + ("sic_codes" -> Json.toJson(s))
       case None => initialRes
     }

    val res2 = companyStatus match {
      case Some(s) => res + ("company_status" -> Json.toJson(s.as[String]))
      case _ => res
    }
    val finalJsonResult = registeredOffice match {
      case Some(s) => {val registeredOfficeAddressPruned = (s.as[JsObject] - ("care_of")
    - ("region") - ("po_box"))
        res2 + ("registered_office_address" -> registeredOfficeAddressPruned)}
      case None => res2
    }
    Some(finalJsonResult)
  }

  private[services] def sicCodesConverter(sicCodes: Option[JsValue]): Option[List[JsObject]] = {
    sicCodes map { codes =>
      codes.as[List[String]] map { sicCode =>
        Json.obj("sic_code" -> sicCode, "sic_description" -> "")
      }
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
