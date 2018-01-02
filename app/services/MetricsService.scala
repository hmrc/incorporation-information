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

import com.kenshoo.play.metrics.{Metrics, MetricsDisabledException}
import com.codahale.metrics.{Counter, Gauge, Timer}
import play.api.Logger
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MetricsServiceImpl @Inject()(val injSubRepo: SubscriptionsMongo,
                                   val injMetrics: Metrics
                                  ) extends MetricsService {
  override val subRepo = injSubRepo.repo
  override val metrics = injMetrics
  override lazy val publicCohoApiSuccessCounter: Counter = metrics.defaultRegistry.counter("public-coho-api-success-counter")
  override lazy val publicCohoApiFailureCounter: Counter = metrics.defaultRegistry.counter("public-coho-api-failure-counter")
  override lazy val transactionApiSuccessCounter: Counter = metrics.defaultRegistry.counter("transaction-api-success-counter")
  override lazy val transactionApiFailureCounter: Counter = metrics.defaultRegistry.counter("transaction-api-failure-counter")
}

trait MetricsService {

  protected val metrics: Metrics
  protected val subRepo: SubscriptionsRepository
  val publicCohoApiSuccessCounter: Counter
  val publicCohoApiFailureCounter: Counter
  val transactionApiSuccessCounter: Counter
  val transactionApiFailureCounter: Counter

  def updateSubscriptionMetrics(): Future[Map[String, Int]] = {
    subRepo.getSubscriptionStats() map {
      m => {
        for( (regime, count) <- m ) {
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
        Logger.warn(s"[MetricsService] [recordSubscriptionRegimeStat] Metrics disabled - ${metricName} -> ${count}")
      }
    }
  }

  def processDataResponseWithMetrics[T](success: Option[Counter] = None, failed: Option[Counter] = None, timer: Option[Timer.Context] = None)(f: => Future[T]): Future[T] = {
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

