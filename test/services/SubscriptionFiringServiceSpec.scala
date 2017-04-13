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

import Helpers.JSONhelpers
import connectors.FiringSubscriptionsConnector
import models.{IncorpUpdate, IncorpUpdateResponse, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import reactivemongo.api.commands.DefaultWriteResult
import repositories.{QueueRepository, SubscriptionsRepository}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec


class SubscriptionFiringServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with JSONhelpers {

  val mockFiringSubsConnector = mock[FiringSubscriptionsConnector]
  val mockQueueRepository = mock[QueueRepository]
  val mockSubscriptionsRepository = mock[SubscriptionsRepository]

  implicit val hc = HeaderCarrier()

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockFiringSubsConnector)
    reset(mockQueueRepository)
    reset(mockSubscriptionsRepository)
  }

  trait mockService extends SubscriptionFiringService {
    val firingSubsConnector = mockFiringSubsConnector
    val queueRepository = mockQueueRepository
    val subscriptionsRepository = mockSubscriptionsRepository

    implicit val hc = HeaderCarrier()

  }

  trait Setup {
    val service = new mockService {}
  }

  val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
  val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now.minusMinutes(2), incorpUpdate)
  val sub = Subscription("transId1", "CT", "subscriber", "www.test.com")
  val incorpUpdateResponse = IncorpUpdateResponse("CT", "subscriber", "www.test.com", incorpUpdate)


  "fireIncorpUpdateBatch" should {
    "return a sequence of true when there is one queued incorp update in the batch and the timestamp of this queued update is in" +
      " the past or present and the subscription successfully fires" in new Setup {
      when(mockQueueRepository.getIncorpUpdates).thenReturn(Seq(queuedIncorpUpdate))
      when(mockSubscriptionsRepository.getSubscriptions(Matchers.any())).thenReturn(Seq(sub), Seq())
      when(mockQueueRepository.removeQueuedIncorpUpdate(sub.transactionId)).thenReturn(true)
      when(mockFiringSubsConnector.connectToAnyURL(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(HttpResponse(200))

      val result = await(service.fireIncorpUpdateBatch)
      result shouldBe Seq(true)
    }

    "return a sequence of false when is one queued incorp update in the batch and the timestamp of this queued update is in" +
      " the future and therefore the subscription is not fired" in new Setup {
      val futureQIU = QueuedIncorpUpdate(DateTime.now.plusMinutes(5), incorpUpdate)
      when(mockQueueRepository.getIncorpUpdates).thenReturn(Seq(futureQIU))

      val result = await(service.fireIncorpUpdateBatch)
      result shouldBe Seq(false)
    }

    "return a sequence of false when a successfully fired subscription has failed to be deleted" in new Setup {
      when(mockQueueRepository.getIncorpUpdates).thenReturn(Seq(queuedIncorpUpdate))
      when(mockSubscriptionsRepository.getSubscriptions(Matchers.any())).thenReturn(Seq(sub))
      when(mockFiringSubsConnector.connectToAnyURL(Matchers.any(), Matchers.any())(Matchers.any())).thenReturn(HttpResponse(200))
      when(mockSubscriptionsRepository.deleteSub(sub.transactionId, sub.regime, sub.subscriber)).thenReturn(DefaultWriteResult(false, 0, Seq(), None, None, None))

      val result = await(service.fireIncorpUpdateBatch)
      result shouldBe Seq(false)
    }
  }


  "checkTimestamp" should {
    "return true when a given timestamp is not in the future" in new Setup {
      val result = await(service.checkTimestamp(DateTime.now.minusMinutes(2)))
      result shouldBe true
    }

    "return false when a given timestamp is in the future" in new Setup {
      val result = await(service.checkTimestamp(DateTime.now.plusMinutes(2)))
      result shouldBe false
    }
  }


}
