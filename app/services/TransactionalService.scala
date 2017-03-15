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

import com.google.inject.ImplementedBy
import connectors.{TransactionalAPIResponse, FailedTransactionalAPIResponse, SuccessfulTransactionalAPIResponse, TransactionalConnector}
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class TransactionalServiceImpl @Inject()(val connector: TransactionalConnector) extends TransactionalService

@ImplementedBy(classOf[TransactionalServiceImpl])
trait TransactionalService {

  protected val connector: TransactionalConnector

  //todo: each function should pull the part of the json the need

  def fetchCompanyProfile(transactionId: String)(implicit hc: HeaderCarrier): Future[Option[JsValue]] = {
    extractJson(connector.fetchTransactionalData(transactionId), "SCRSCompanyProfile")
  }

  def fetchOfficerList(transactionId: String)(implicit hc: HeaderCarrier) = {
    extractJson(connector.fetchTransactionalData(transactionId), "SCRSCompanyOfficerList")
  }

  def fetchOfficerAppointments(transactionId: String, officerId: String)(implicit hc: HeaderCarrier) = {
    //todo: need API schema to see how to extract by officer id
    connector.fetchTransactionalData(transactionId)
  }

  def fetchOfficerDisqualifications(transactionId: String, officerId: String)(implicit hc: HeaderCarrier) = {
    //todo: need API schema to see how to extract by officer id
    connector.fetchTransactionalData(transactionId)
  }

  private def extractJson(f: => Future[TransactionalAPIResponse], key: String) = {
    f.map {
      case SuccessfulTransactionalAPIResponse(json) => (json \ key).toOption
      case FailedTransactionalAPIResponse => None
    }
  }
}
