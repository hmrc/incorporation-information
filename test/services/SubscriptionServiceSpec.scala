/*
 * Copyright 2021 HM Revenue & Customs
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
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}
import repositories._
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SubscriptionServiceSpec extends SCRSSpec {

  val mockSubRepo = mock[SubscriptionsMongoRepository]
  val mockIncorpRepo = mock[IncorpUpdateMongoRepository]
  val mockIncorpUpdateService = mock[IncorpUpdateService]

  val now = DateTime.now()
  val subDelay = 5

  trait Setup {

    reset(mockSubRepo, mockIncorpRepo, mockIncorpUpdateService)

    val service = new SubscriptionService {
      override val subRepo = mockSubRepo
      override val incorpRepo = mockIncorpRepo
      override val incorpUpdateService = mockIncorpUpdateService
      override protected val forcedSubDelay = subDelay
    }

    def mockGetIncorpUpdate(transactionId: String, returnedIncorpUpdate: Option[IncorpUpdate]) = {
      when(mockIncorpRepo.getIncorpUpdate(eqTo(transactionId))).thenReturn(Future.successful(returnedIncorpUpdate))
    }

    def mockInsertSubscription(sub: Subscription, upsertResult: UpsertResult) = {
      when(mockSubRepo.insertSub(eqTo(sub))).thenReturn(Future.successful(upsertResult))
    }

    def mockUpsertToQueue(success: Boolean) = when(mockIncorpUpdateService.upsertToQueue(any())).thenReturn(Future.successful(success))

    implicit class incorpUpdateImplicits(iu: IncorpUpdate) {
      def toQueuedIncorpUpdate: QueuedIncorpUpdate = QueuedIncorpUpdate(now, iu)
    }
  }

  val transId = "transId123"
  val incorpUpdate = IncorpUpdate(transId, "Accepted", Some("123456"), None, "timepoint", None)
  val regime = "CT100"
  val subscriber = "abc123"
  val url = "www.test.com"
  val sub = Subscription(transId, regime, subscriber, url)


  "checkForIncorpUpdate" should {

    "return an incorp update for a subscription that exists" in new Setup {
      when(mockIncorpRepo.getIncorpUpdate(eqTo(transId))).thenReturn(Future.successful(Some(incorpUpdate)))

      val result = await(service.checkForIncorpUpdate(transId))
      result.get shouldBe incorpUpdate
    }

    "return a None result for when an added subscription that does not yet exist" in new Setup {
      when(mockIncorpRepo.getIncorpUpdate(eqTo(transId))).thenReturn(Future(None))

      val result = await(service.checkForIncorpUpdate(transId))
      result shouldBe None
    }
  }

  "checkForSubscription" should {

    "return a SuccessfulSub" when {

      "an incorp update does not exist for the supplied transaction id and a subscription is placed" in new Setup {
        val upsertResult = UpsertResult(0, 1, Seq())

        mockGetIncorpUpdate(transId, None)
        mockInsertSubscription(sub, upsertResult)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = false))

        result shouldBe SuccessfulSub(false)
      }

      "an incorp update exists for the supplied transaction id but the forced flag is set to true" in new Setup {
        val upsertResult = UpsertResult(0, 1, Seq())
        val queuedIncorpUpdate = incorpUpdate.toQueuedIncorpUpdate

        mockGetIncorpUpdate(transId, Some(incorpUpdate))
        mockInsertSubscription(sub, upsertResult)
        when(mockIncorpUpdateService.createQueuedIncorpUpdate(any(), any())).thenReturn(queuedIncorpUpdate)
        mockUpsertToQueue(true)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result shouldBe SuccessfulSub(true)
      }
    }

    "return an IncorpExists if an incorp update exists for the supplied transaction id and the forced flag is set to false" in new Setup {
      val upsertResult = UpsertResult(0, 1, Seq())

      mockGetIncorpUpdate(transId, Some(incorpUpdate))

      val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = false))

      result shouldBe IncorpExists(incorpUpdate)
    }

    "return a FailedSub" when {

      "an incorp update does not exist for the supplied transaction id and the subscription fails to get inserted" in new Setup {
        val upsertResult = UpsertResult(0, 0, Seq(WriteError(0, 11111, "fail")))

        mockGetIncorpUpdate(transId, None)
        mockInsertSubscription(sub, upsertResult)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result shouldBe FailedSub
      }

      "an incorp update exists for the supplied transaction id but the forced flag is set to true and and fails to insert a subscription" in new Setup {
        val upsertResult = UpsertResult(0, 0, Seq(WriteError(0, 11111, "fail")))

        mockGetIncorpUpdate(transId, Some(incorpUpdate))
        mockInsertSubscription(sub, upsertResult)
        mockUpsertToQueue(false)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result shouldBe FailedSub
      }

      "an incorp update exists for the supplied transaction id but the forced flag is set to true and and fails to copy the incorp update to the queue" in new Setup {
        val upsertResult = UpsertResult(1, 0, Seq())

        mockGetIncorpUpdate(transId, Some(incorpUpdate))
        mockInsertSubscription(sub, upsertResult)
        mockUpsertToQueue(false)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result shouldBe FailedSub
      }
    }
  }

  "addSubscription" should {

    "return a SuccessfulSub when a new subscription has been added" in new Setup {
       val ur: UpsertResult = UpsertResult(0, 0, Seq())
      when(mockSubRepo.insertSub(sub)).thenReturn(Future(ur))

      val result = await(service.addSubscription(transId, regime, subscriber, url))
      result shouldBe SuccessfulSub(false)
    }

    "return a FailedSub when a new subscription fails to be added" in new Setup {
      val errors = Seq(WriteError(1, 1, "An error occured"))
      val ur: UpsertResult = UpsertResult(0, 0, errors)
      when(mockSubRepo.insertSub(sub)).thenReturn(Future(ur))

      val result = await(service.addSubscription(transId, regime, subscriber, url))
      result shouldBe FailedSub
    }
  }


  "deleteSubscription" should {
    "return a DeletedSub when an existing subscription is deleted" in new Setup {
      val writeResult = DefaultWriteResult(true, 1, Seq(), None, None, None).flatten
      when(mockSubRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(writeResult))

      val result = await(service.deleteSubscription(transId, regime, subscriber))
      result shouldBe DeletedSub
    }

    "return a NotDeletedSub when an existing subscription has failed to be deleted" in new Setup {
      val writeResult = DefaultWriteResult(false, 1, Seq(), None, None, None).flatten
      when(mockSubRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(writeResult))

      val result = await(service.deleteSubscription(transId, regime, subscriber))
      result shouldBe NotDeletedSub
    }
  }


  "getSubscription" should {

    "return a Subscription when a Subscription exists" in new Setup {
      when(mockSubRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(Some(sub)))

      val result = service.getSubscription(transId, regime, subscriber)
      await(result) shouldBe Some(sub)
    }

    "return None when a Subscription does not exist" in new Setup {
      when(mockSubRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(None))

      val result = service.getSubscription(transId, regime, subscriber)
      await(result) shouldBe None
    }
  }
}
