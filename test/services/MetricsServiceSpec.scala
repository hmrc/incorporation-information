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

import Helpers.SCRSSpec
import com.codahale.metrics.{Histogram, MetricRegistry}
import com.kenshoo.play.metrics.Metrics
import models.{IncorpUpdate, Subscription}
import org.mockito.Matchers
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MetricsServiceSpec extends SCRSSpec with BeforeAndAfterEach {

  val mockHisto1 = mock[Histogram]
  val mockHisto2 = mock[Histogram]
  val mockHisto3 = mock[Histogram]
  val mockSubRepo = mock[SubscriptionsMongoRepository]
  val mockRegistry = mock[MetricRegistry]
  val mockMetrics = mock[Metrics]

  trait Setup {

    val service = new MetricsService {
      override val metrics = mockMetrics
      override val subRepo = mockSubRepo
   }
  }

  override def beforeEach() = {
    Seq(mockMetrics, mockHisto1, mockHisto2, mockHisto3, mockSubRepo, mockRegistry) foreach { reset(_) }
  }

  val transId = "transId123"
  val incorpUpdate = IncorpUpdate(transId, "Accepted", Some("123456"), None, "timepoint", None)
  val regime = "CT100"
  val subscriber = "abc123"
  val url = "www.test.com"
  val sub = Subscription(transId, regime, subscriber, url)


  "Metrics" should {

    "update no metrics if no subscriptions" in new Setup {
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Map[String, Int]())

      val result = await(service.updateSubscriptionMetrics())

      result shouldBe Map()

      verifyNoMoreInteractions(mockRegistry)
      verifyNoMoreInteractions(mockHisto1)
    }

    "update a single metric when one is supplied" in new Setup {
      when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Map("wibble" -> 1))

      val result = await(service.updateSubscriptionMetrics())

      result shouldBe Map("wibble" -> 1)

      verify(mockRegistry).remove(Matchers.contains("wibble"))
      verify(mockRegistry).register(Matchers.contains("wibble"), Matchers.any())
      verifyNoMoreInteractions(mockRegistry)
    }

    "update a multiple metrics when required" in new Setup {
      when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Map("foo1" -> 1, "foo2" -> 2, "foo3" -> 3))

      val result = await(service.updateSubscriptionMetrics())

      result shouldBe Map("foo1" -> 1, "foo2" -> 2, "foo3" -> 3)

      verify(mockRegistry).remove(Matchers.contains("foo1"))
      verify(mockRegistry).register(Matchers.contains("foo1"), Matchers.any())
      verify(mockRegistry).remove(Matchers.contains("foo2"))
      verify(mockRegistry).register(Matchers.contains("foo2"), Matchers.any())
      verify(mockRegistry).remove(Matchers.contains("foo3"))
      verify(mockRegistry).register(Matchers.contains("foo3"), Matchers.any())
      verifyNoMoreInteractions(mockRegistry)
    }
  }
}
