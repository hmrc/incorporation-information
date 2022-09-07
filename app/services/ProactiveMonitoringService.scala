/*
 * Copyright 2022 HM Revenue & Customs
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

import config.MicroserviceConfig
import connectors.{IncorporationAPIConnector, PublicCohoApiConnectorImpl, SuccessfulTransactionalAPIResponse}
import jobs._
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.LockService
import utils.Base64

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class ProactiveMonitoringServiceImpl @Inject()(val transactionalConnector: IncorporationAPIConnector,
                                               val publicCohoConnector: PublicCohoApiConnectorImpl,
                                               msConfig: MicroserviceConfig,
                                               val lockRepositoryProvider: LockRepositoryProvider) extends ProactiveMonitoringService {
  protected val transactionIdToPoll: String = msConfig.transactionIdToPoll
  protected val crnToPoll: String = msConfig.crnToPoll

  lazy val lockoutTimeout = msConfig.getConfigInt("schedules.proactive-monitoring-job.lockTimeout")

  lazy val lockKeeper: LockService = LockService(lockRepositoryProvider.repo, "proactive-monitoring-job-lock", lockoutTimeout.seconds)
}

trait ProactiveMonitoringService extends ScheduledService[Either[(String, String), LockResponse]] with Logging {

  val lockKeeper: LockService
  protected val transactionalConnector: IncorporationAPIConnector
  protected val publicCohoConnector: PublicCohoApiConnectorImpl

  protected val transactionIdToPoll: String
  protected val crnToPoll: String

  lazy val decodedTxId: String = Base64.decode(transactionIdToPoll)
  lazy val decodedCrn: String = Base64.decode(crnToPoll)

  def invoke(implicit ec: ExecutionContext): Future[Either[(String, String), LockResponse]] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    lockKeeper.withLock(pollAPIs).map {
      case Some(res) =>
        logger.info("ProactiveMonitoringService acquired lock and returned results")
        logger.info(s"Result: $res")
        Left(res)
      case None =>
        logger.info("ProactiveMonitoringService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e: Exception => logger.error(s"Error running pollAPIs with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }

  def pollAPIs(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[(String, String)] = {
    for {
      txRes <- pollTransactionalAPI
      pubRes <- pollPublicAPI
    } yield (s"polling transactional API - $txRes", s"polling public API - $pubRes")
  }

  private[services] def pollTransactionalAPI(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    transactionalConnector.fetchTransactionalData(decodedTxId) map {
      case SuccessfulTransactionalAPIResponse(_) => "success"
      case _ => "failed"
    }
  }

  private[services] def pollPublicAPI(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    publicCohoConnector.getCompanyProfile(decodedCrn).map {
      _.fold("failed")(_ => "success")
    }
  }
}