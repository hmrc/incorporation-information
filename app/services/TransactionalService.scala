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
import play.api.libs.json._
import repositories.IncorpUpdateRepository
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransactionalServiceImpl @Inject()(val connector: IncorporationAPIConnector,val incorpRepos:IncorpUpdateRepository ) extends TransactionalService

trait TransactionalService {

  protected val connector: IncorporationAPIConnector
  val incorpRepos: IncorpUpdateRepository

  def fetchCompanyProfile(transactionId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    val transformer = (JsPath \ "officers").json.prune
    extractJson(connector.fetchTransactionalData(transactionId), transformer)
  }



  def fetchOfficerList(transactionId: String)(implicit hc: HeaderCarrier) = {
    connector.fetchTransactionalData(transactionId) map {
      case SuccessfulTransactionalAPIResponse(js) => (js \ "officers").toOption
      case _ => None
    }
  }

  def checkIfCompIncorporated(transactionId:String)(implicit hc:HeaderCarrier): Future[Option[String]] = {
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
