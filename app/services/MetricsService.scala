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

import com.kenshoo.play.metrics.{Metrics, MetricsDisabledException}
import play.api.Logger
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MetricsServiceImpl @Inject()(
                                         val injSubRepo: SubscriptionsMongo,
                                         val injMetrics: Metrics
                                       ) extends MetricsService {
  override val subRepo = injSubRepo.repo
  override val metrics = injMetrics
}

trait MetricsService {

  protected val metrics: Metrics
  protected val subRepo: SubscriptionsRepository

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
    val metricName = s"subscription-regime.${regime}"
    try {
      val subRegimeStat = metrics.defaultRegistry.histogram(metricName)
      subRegimeStat.update(count)
    } catch {
      case ex: MetricsDisabledException => {
        Logger.warn(s"[MetricsService] [recordSubscriptionRegimeStat] Metrics disabled - ${metricName} -> ${count}")
      }
    }
  }

}

