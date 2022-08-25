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

import com.codahale.metrics.{Counter, Gauge, Timer}
import com.kenshoo.play.metrics.{Metrics, MetricsDisabledException}
import config.MicroserviceConfig
import jobs._
import play.api.Logging
import repositories._
import uk.gov.hmrc.mongo.lock.LockService

import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}


class MetricsServiceImpl @Inject()(val injSubRepo: SubscriptionsMongo,
                                   val injMetrics: Metrics,
                                   val msConfig: MicroserviceConfig,
                                   val lockRepositoryProvider: LockRepositoryProvider
                                  ) extends MetricsService {
  override val subRepo = injSubRepo.repo
  override val metrics = injMetrics
  override lazy val publicCohoApiSuccessCounter: Counter = metrics.defaultRegistry.counter("public-coho-api-success-counter")
  override lazy val publicCohoApiFailureCounter: Counter = metrics.defaultRegistry.counter("public-coho-api-failure-counter")
  override lazy val transactionApiSuccessCounter: Counter = metrics.defaultRegistry.counter("transaction-api-success-counter")
  override lazy val transactionApiFailureCounter: Counter = metrics.defaultRegistry.counter("transaction-api-failure-counter")

  override val publicAPITimer: Timer = metrics.defaultRegistry.timer("public-api-timer")
  override val internalAPITimer: Timer = metrics.defaultRegistry.timer("internal-api-timer")

  lazy val lockoutTimeout = msConfig.getConfigInt("schedules.metrics-job.lockTimeout")

  lazy val lockKeeper: LockService = LockService(lockRepositoryProvider.repo, "metrics-job-lock", lockoutTimeout.seconds)
}

trait MetricsService extends ScheduledService[Either[Map[String, Int], LockResponse]] with Logging {

  protected val metrics: Metrics
  protected val subRepo: SubscriptionsRepository
  val publicCohoApiSuccessCounter: Counter
  val publicCohoApiFailureCounter: Counter
  val transactionApiSuccessCounter: Counter
  val transactionApiFailureCounter: Counter
  val lockKeeper: LockService

  val publicAPITimer: Timer
  val internalAPITimer: Timer

  def invoke(implicit ec: ExecutionContext): Future[Either[Map[String, Int], LockResponse]] = {
    lockKeeper.withLock(updateSubscriptionMetrics).map {
      case Some(res) =>
        logger.info("MetricsService acquired lock and returned results")
        logger.info(s"Result: $res")
        Left(res)
      case None =>
        logger.info("MetricsService cant acquire lock")
        Right(MongoLocked)
    }.recover {
      case e: Exception => logger .error(s"Error running updateSubscriptionMetrics with message: ${e.getMessage}")
        Right(UnlockingFailed)
    }
  }

  def updateSubscriptionMetrics()(implicit ec: ExecutionContext): Future[Map[String, Int]] = {
    subRepo.getSubscriptionStats() map {
      m => {
        for ((regime, count) <- m) {
          recordSubscriptionRegimeStat(regime, count)
        }

        m
      }
    }
  }

  private def recordSubscriptionRegimeStat(regime: String, count: Int) = {
    val metricName = s"subscription-regime-stat.${regime}"
    try {
      val gauge = new Gauge[Int] {
        val getValue = count
      }

      metrics.defaultRegistry.remove(metricName)
      metrics.defaultRegistry.register(metricName, gauge)
    } catch {
      case ex: MetricsDisabledException => {
        logger.warn(s"[MetricsService] [recordSubscriptionRegimeStat] Metrics disabled - ${metricName} -> ${count}")
      }
    }
  }

  def processDataResponseWithMetrics[T](success: Option[Counter] = None,
                                        failed: Option[Counter] = None,
                                        timer: Option[Timer.Context] = None
                                       )(f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f map { data =>
      timer.map(_.stop())
      success.map(_.inc(1))
      data
    } recover {
      case e =>
        timer.map(_.stop())
        failed.map(_.inc(1))
        throw e
    }
  }
}