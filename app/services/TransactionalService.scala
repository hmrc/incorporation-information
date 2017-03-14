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
import connectors.TransactionalConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global

class TransactionalServiceImpl @Inject()(val connector: TransactionalConnector) extends TransactionalService

@ImplementedBy(classOf[TransactionalServiceImpl])
trait TransactionalService {

  val connector: TransactionalConnector

  def fetchCompanyProfile(transactionId: String)(implicit hc: HeaderCarrier) = {
    connector.fetchCompanyProfile(transactionId).map{ p =>
      println("====================" + p)
      p
    }
  }

}
