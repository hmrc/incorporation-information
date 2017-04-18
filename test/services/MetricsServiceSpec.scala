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
import com.codahale.metrics.Histogram
import com.kenshoo.play.metrics.Metrics
import models.{IncorpUpdate, Subscription}
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}
import repositories._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class MetricsServiceSpec extends SCRSSpec {

  val mockHisto = mock[Histogram]
  val mockSubRepo = mock[SubscriptionsMongoRepository]

  trait Setup {

    val service = new MetricsService {
      override val subRegimeStat = mockHisto
      override val subRepo = mockSubRepo
   }
  }

  val transId = "transId123"
  val incorpUpdate = IncorpUpdate(transId, "Accepted", Some("123456"), None, "timepoint", None)
  val regime = "CT100"
  val subscriber = "abc123"
  val url = "www.test.com"
  val sub = Subscription(transId, regime, subscriber, url)


  "xxx" should {
    "yyy" in new Setup {
      when(mockSubRepo.getSubscriptionStats()).thenReturn(Map("wibble" -> 1))

      val result = await(service.updateSubscriptionMetrics())

      result shouldBe Map("wibble" -> 1)
    }
  }
}
