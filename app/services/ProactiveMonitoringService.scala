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

import javax.inject.{Inject, Singleton}

import config.MicroserviceConfig
import connectors.{IncorporationAPIConnector, PublicCohoApiConnector, SuccessfulTransactionalAPIResponse}
import utils.Base64

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class ProactiveMonitoringServiceImpl @Inject()(val transactionalConnector: IncorporationAPIConnector,
                                               val publicCohoConnector: PublicCohoApiConnector,
                                               config: MicroserviceConfig) extends ProactiveMonitoringService {
  protected val transactionIdToPoll: String = config.transactionIdToPoll
  protected val crnToPoll: String = config.crnToPoll
}

trait ProactiveMonitoringService {

  protected val transactionalConnector: IncorporationAPIConnector
  protected val publicCohoConnector: PublicCohoApiConnector

  protected val transactionIdToPoll: String
  protected val crnToPoll: String

  lazy val decodedTxId: String = Base64.decode(transactionIdToPoll)
  lazy val decodedCrn: String = Base64.decode(crnToPoll)

  def pollAPIs(implicit hc: HeaderCarrier): Future[(String, String)] = {
    for {
      txRes <- pollTransactionalAPI
      pubRes <- pollPublicAPI
    } yield (s"polling transactional API - $txRes", s"polling public API - $pubRes")
  }

  private[services] def pollTransactionalAPI(implicit hc: HeaderCarrier): Future[String] = {
    transactionalConnector.fetchTransactionalData(decodedTxId) map {
      case SuccessfulTransactionalAPIResponse(_) => "success"
      case _ => "failed"
    }
  }

  private[services] def pollPublicAPI(implicit hc: HeaderCarrier): Future[String] = {
    publicCohoConnector.getCompanyProfile(decodedCrn).map{
      _.fold("failed")(_ => "success")
    }
  }
}
