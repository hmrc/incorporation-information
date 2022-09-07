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

import Helpers.{JSONhelpers, SCRSSpec}
import com.mongodb.client.result.DeleteResult
import connectors.FiringSubscriptionsConnector
import models.{IncorpUpdate, IncorpUpdateResponse, QueuedIncorpUpdate, Subscription}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import repositories.{QueueRepository, SubscriptionsRepository}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.mongo.lock.LockService
import utils.DateCalculators

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class SubscriptionFiringServiceSpec extends SCRSSpec with BeforeAndAfterEach with JSONhelpers with DateCalculators {

  val mockFiringSubsConnector: FiringSubscriptionsConnector = mock[FiringSubscriptionsConnector]
  val mockQueueRepository: QueueRepository = mock[QueueRepository]
  val mockSubscriptionsRepository: SubscriptionsRepository = mock[SubscriptionsRepository]
  val mockLockService: LockService = mock[LockService]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks(): Unit = {
    reset(mockFiringSubsConnector)
    reset(mockQueueRepository)
    reset(mockSubscriptionsRepository)
    reset(mockLockService)
  }

  trait mockService extends SubscriptionFiringService {
    val firingSubsConnector: FiringSubscriptionsConnector = mockFiringSubsConnector
    val queueRepository: QueueRepository = mockQueueRepository
    val subscriptionsRepository: SubscriptionsRepository = mockSubscriptionsRepository
    val queueFailureDelay = 10
    val queueRetryDelay = 5
    val fetchSize = 1

    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  class Setup(use: Boolean = false) {
    val service: mockService = new mockService {
      override val lockKeeper: LockService = mockLockService
      override val useHttpsFireSubs: Boolean = use
      override implicit val ec: ExecutionContext = global
    }
  }

  val incorpUpdate: IncorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
  val queuedIncorpUpdate: QueuedIncorpUpdate = QueuedIncorpUpdate(getDateTimeNowUTC.minusMinutes(2), incorpUpdate)
  val sub: Subscription = Subscription("transId1", "CT", "subscriber", "www.test.com")
  val incorpUpdateResponse: IncorpUpdateResponse = IncorpUpdateResponse("CT", "subscriber", "www.test.com", incorpUpdate)


  "fireIncorpUpdateBatch" must {
    "return a sequence of true when there is one queued incorp update in the batch and the timestamp of this queued update is in" +
      " the past or present and the subscription successfully fires" in new Setup {
      when(mockQueueRepository.getIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(queuedIncorpUpdate)))
      when(mockSubscriptionsRepository.getSubscriptions(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(sub)), Future.successful(Seq()))
      when(mockQueueRepository.removeQueuedIncorpUpdate(sub.transactionId)).thenReturn(Future.successful(true))
      when(mockFiringSubsConnector.connectToAnyURL(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(200)))
      when(mockQueueRepository.updateTimestamp(any(), any())).thenReturn(Future.successful(true))

      val result: Seq[Boolean] = await(service.fireIncorpUpdateBatch)
      result mustBe Seq(true)
    }

    "return a sequence of false when is one queued incorp update in the batch and the timestamp of this queued update is in" +
      " the future and therefore the subscription is not fired" in new Setup {
      val queuedIncorpUpdate: QueuedIncorpUpdate = QueuedIncorpUpdate(getDateTimeNowUTC.plusMinutes(5), incorpUpdate)
      when(mockQueueRepository.getIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(queuedIncorpUpdate)))

      val result: Seq[Boolean] = await(service.fireIncorpUpdateBatch)
      result mustBe Seq(false)
    }

    "return a sequence of false when a successfully fired subscription has failed to be deleted" in new Setup {
      when(mockQueueRepository.getIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(queuedIncorpUpdate)))
      when(mockSubscriptionsRepository.getSubscriptions(ArgumentMatchers.any())).thenReturn(Future.successful(Seq(sub)))
      when(mockFiringSubsConnector.connectToAnyURL(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(HttpResponse(200)))
      when(mockSubscriptionsRepository.deleteSub(sub.transactionId, sub.regime, sub.subscriber)).thenReturn(Future.successful(DeleteResult.acknowledged(0)))

      val result: Seq[Boolean] = await(service.fireIncorpUpdateBatch)
      result mustBe Seq(false)
    }
  }


  "checkTimestamp" must {
    "return true when a given timestamp is not in the future" in new Setup {
      val result: Boolean = service.checkTimestamp(getDateTimeNowUTC.minusMinutes(2))
      result mustBe true
    }

    "return false when a given timestamp is in the future" in new Setup {
      val result: Boolean = service.checkTimestamp(getDateTimeNowUTC.plusMinutes(2))
      result mustBe false
    }
  }
  "httpHttpsConverter" must {
    "convert a url to https if useHttpsFireSubs == true" in new Setup(true) {
      val expectedUrl = "https://foo"
      service.httpHttpsConverter("http://foo") mustBe expectedUrl
    }
    "convert a url to https and replace 443 if useHttpsFireSubs == true" in new Setup(true) {
      val expectedUrl = "https://foo:443"
      service.httpHttpsConverter("http://foo:80") mustBe expectedUrl
    }
    "do not convert a url if useHttpsFireSubs == false" in new Setup(false) {
      val expectedUrl = "http://foo:80"
      service.httpHttpsConverter(expectedUrl) mustBe expectedUrl
    }
  }

  "fireIncorpUpdate" must {
    "recover if an exception is thrown from the connector" in new Setup {
      when(mockSubscriptionsRepository.getSubscriptions(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Seq(sub)))
      when(mockFiringSubsConnector.connectToAnyURL(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.failed(new RuntimeException))

      val res: RuntimeException = intercept[RuntimeException](await(service.fireIncorpUpdate(queuedIncorpUpdate)))

      verify(mockQueueRepository).updateTimestamp(ArgumentMatchers.eq(sub.transactionId), ArgumentMatchers.any())
    }
    "update timestamp if 202 is received from connector" in new Setup {
      when(mockSubscriptionsRepository.getSubscriptions(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Seq(sub)))
      when(mockFiringSubsConnector.connectToAnyURL(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(202)))
      when(mockQueueRepository.updateTimestamp(any(), any())).thenReturn(Future.successful(true))

      await(service.fireIncorpUpdate(queuedIncorpUpdate))

      verify(mockQueueRepository).updateTimestamp(ArgumentMatchers.eq(sub.transactionId), ArgumentMatchers.any())
    }
    "delete subscription when a 200 is received from connector" in new Setup {
      when(mockSubscriptionsRepository.getSubscriptions(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Seq(sub)))
      when(mockFiringSubsConnector.connectToAnyURL(ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200)))
      when(mockSubscriptionsRepository.deleteSub(any(), any(), any())).thenReturn(Future.successful(DeleteResult.acknowledged(1)))

      await(service.fireIncorpUpdate(queuedIncorpUpdate))

      verify(mockSubscriptionsRepository).deleteSub(ArgumentMatchers.eq(sub.transactionId), ArgumentMatchers.any(), ArgumentMatchers.any())
    }
  }
}
