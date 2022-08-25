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

import Helpers.SCRSSpec
import com.codahale.metrics.{Counter, Histogram, MetricRegistry, Timer}
import com.kenshoo.play.metrics.Metrics
import models.{IncorpUpdate, Subscription}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import repositories._
import uk.gov.hmrc.mongo.lock.LockService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetricsServiceSpec extends SCRSSpec with BeforeAndAfterEach {

  val mockHisto1 = mock[Histogram]
  val mockHisto2 = mock[Histogram]
  val mockHisto3 = mock[Histogram]
  val mockSubRepo = mock[SubscriptionsMongoRepository]
  val mockRegistry = mock[MetricRegistry]
  val mockMetrics = mock[Metrics]
  val mockCounter = mock[Counter]
  val mockTimer = mock[Timer.Context]
  val mockTimerMetric = mock[Timer]
  val mockLockService: LockService = mock[LockService]


  trait Setup {

    val service = new MetricsService {
      override val metrics = mockMetrics
      override val subRepo = mockSubRepo
      override val publicCohoApiFailureCounter: Counter = mockCounter
      override val publicCohoApiSuccessCounter: Counter = mockCounter
      override val transactionApiFailureCounter: Counter = mockCounter
      override val transactionApiSuccessCounter: Counter = mockCounter
      override val publicAPITimer = mockTimerMetric
      override val internalAPITimer = mockTimerMetric
      override val lockKeeper = mockLockService
    }
  }

  override def beforeEach() = {
    Seq(mockMetrics, mockHisto1, mockHisto2, mockHisto3, mockSubRepo, mockRegistry, mockCounter, mockTimer, mockLockService) foreach { reset(_) }
  }

  val transId = "transId123"
  val incorpUpdate = IncorpUpdate(transId, "Accepted", Some("123456"), None, "timepoint", None)
  val regime = "CT100"
  val subscriber = "abc123"
  val url = "www.test.com"
  val sub = Subscription(transId, regime, subscriber, url)


  "Metrics" must {

    "update no metrics if no subscriptions" in new Setup {
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Future.successful(Map[String, Int]()))

      val result = await(service.updateSubscriptionMetrics())

      result mustBe Map()

      verifyNoMoreInteractions(mockRegistry)
      verifyNoMoreInteractions(mockHisto1)
    }

    "update a single metric when one is supplied" in new Setup {
      when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Future.successful(Map("wibble" -> 1)))

      val result = await(service.updateSubscriptionMetrics())

      result mustBe Map("wibble" -> 1)

      verify(mockRegistry).remove(ArgumentMatchers.contains("wibble"))
      verify(mockRegistry).register(ArgumentMatchers.contains("wibble"), ArgumentMatchers.any())
      verifyNoMoreInteractions(mockRegistry)
    }

    "update a multiple metrics when required" in new Setup {
      when(mockMetrics.defaultRegistry).thenReturn(mockRegistry)
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Future.successful(Map("foo1" -> 1, "foo2" -> 2, "foo3" -> 3)))

      val result = await(service.updateSubscriptionMetrics())

      result mustBe Map("foo1" -> 1, "foo2" -> 2, "foo3" -> 3)

      verify(mockRegistry).remove(ArgumentMatchers.contains("foo1"))
      verify(mockRegistry).register(ArgumentMatchers.contains("foo1"), ArgumentMatchers.any())
      verify(mockRegistry).remove(ArgumentMatchers.contains("foo2"))
      verify(mockRegistry).register(ArgumentMatchers.contains("foo2"), ArgumentMatchers.any())
      verify(mockRegistry).remove(ArgumentMatchers.contains("foo3"))
      verify(mockRegistry).register(ArgumentMatchers.contains("foo3"), ArgumentMatchers.any())
      verifyNoMoreInteractions(mockRegistry)
    }
  }

  "calling processDataResponseWithMatrics" must {

    "should return the resut of the input function if a timer and counters are passed through" in new Setup {
      val result = await(service.processDataResponseWithMetrics[Int](Some(mockCounter),
        Some(mockCounter),Some(mockTimer))(Future.successful(1 + 1)))

      result mustBe 2
    }
    "should return the result of the input function when called with a success and failure counter" in new Setup {
      val result = await(service.processDataResponseWithMetrics[Int](Some(mockCounter),
        Some(mockCounter))(Future.successful(1 + 1)))

      result mustBe 2
    }
    "should return the result of the input function when called with a success counter" in new Setup {
      val result = await(service.processDataResponseWithMetrics[Int](Some(mockCounter))(Future.successful(1 + 1)))

      result mustBe 2
    }
    "should return the result of the input function when called with no parameters" in new Setup {
      val result = await(service.processDataResponseWithMetrics[Int]()(Future.successful(1 + 1)))

      result mustBe 2
    }
  }
}
