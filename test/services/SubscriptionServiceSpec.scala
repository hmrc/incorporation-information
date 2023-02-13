/*
 * Copyright 2023 HM Revenue & Customs
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
import com.mongodb.client.result.DeleteResult
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.mongodb.scala.WriteError
import org.mongodb.scala.bson.BsonDocument
import play.api.test.Helpers._
import repositories._
import utils.DateCalculators

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class SubscriptionServiceSpec extends SCRSSpec with DateCalculators {

  val mockSubRepo = mock[SubscriptionsMongoRepository]
  val mockIncorpRepo = mock[IncorpUpdateMongoRepository]
  val mockIncorpUpdateService = mock[IncorpUpdateService]

  val now = getDateTimeNowUTC
  val subDelay = 5

  trait Setup {

    reset(mockSubRepo, mockIncorpRepo, mockIncorpUpdateService)

    val service = new SubscriptionService {
      override val subRepo = mockSubRepo
      override val incorpRepo = mockIncorpRepo
      override val incorpUpdateService = mockIncorpUpdateService
      override protected val forcedSubDelay = subDelay
      override implicit val ec: ExecutionContext = global
    }

    def mockGetIncorpUpdate(transactionId: String, returnedIncorpUpdate: Option[IncorpUpdate]) = {
      when(mockIncorpRepo.getIncorpUpdate(eqTo(transactionId))).thenReturn(Future.successful(returnedIncorpUpdate))
    }

    def mockInsertSubscription(sub: Subscription, upsertResult: UpsertResult) = {
      when(mockSubRepo.insertSub(eqTo(sub))).thenReturn(Future.successful(upsertResult))
    }

    def mockUpsertToQueue(success: Boolean) = when(mockIncorpUpdateService.upsertToQueue(any())(any[ExecutionContext])).thenReturn(Future.successful(success))

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


  "checkForIncorpUpdate" must {

    "return an incorp update for a subscription that exists" in new Setup {
      when(mockIncorpRepo.getIncorpUpdate(eqTo(transId))).thenReturn(Future.successful(Some(incorpUpdate)))

      val result = await(service.checkForIncorpUpdate(transId))
      result.get mustBe incorpUpdate
    }

    "return a None result for when an added subscription that does not yet exist" in new Setup {
      when(mockIncorpRepo.getIncorpUpdate(eqTo(transId))).thenReturn(Future(None))

      val result = await(service.checkForIncorpUpdate(transId))
      result mustBe None
    }
  }

  "checkForSubscription" must {

    "return a SuccessfulSub" when {

      "an incorp update does not exist for the supplied transaction id and a subscription is placed" in new Setup {
        val upsertResult = UpsertResult(0, 1, Seq())

        mockGetIncorpUpdate(transId, None)
        mockInsertSubscription(sub, upsertResult)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = false))

        result mustBe SuccessfulSub(false)
      }

      "an incorp update exists for the supplied transaction id but the forced flag is set to true" in new Setup {
        val upsertResult = UpsertResult(0, 1, Seq())
        val queuedIncorpUpdate = incorpUpdate.toQueuedIncorpUpdate

        mockGetIncorpUpdate(transId, Some(incorpUpdate))
        mockInsertSubscription(sub, upsertResult)
        when(mockIncorpUpdateService.createQueuedIncorpUpdate(any(), any())).thenReturn(queuedIncorpUpdate)
        mockUpsertToQueue(true)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result mustBe SuccessfulSub(true)
      }
    }

    "return an IncorpExists if an incorp update exists for the supplied transaction id and the forced flag is set to false" in new Setup {
      val upsertResult = UpsertResult(0, 1, Seq())

      mockGetIncorpUpdate(transId, Some(incorpUpdate))

      val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = false))

      result mustBe IncorpExists(incorpUpdate)
    }

    "return a FailedSub" when {

      "an incorp update does not exist for the supplied transaction id and the subscription fails to get inserted" in new Setup {
        val upsertResult = UpsertResult(0, 0, Seq(new WriteError(11111, "fail", BsonDocument())))

        mockGetIncorpUpdate(transId, None)
        mockInsertSubscription(sub, upsertResult)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result mustBe FailedSub
      }

      "an incorp update exists for the supplied transaction id but the forced flag is set to true and and fails to insert a subscription" in new Setup {
        val upsertResult = UpsertResult(0, 0, Seq(new WriteError(11111, "fail", BsonDocument())))

        mockGetIncorpUpdate(transId, Some(incorpUpdate))
        mockInsertSubscription(sub, upsertResult)
        mockUpsertToQueue(false)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result mustBe FailedSub
      }

      "an incorp update exists for the supplied transaction id but the forced flag is set to true and and fails to copy the incorp update to the queue" in new Setup {
        val upsertResult = UpsertResult(1, 0, Seq())

        mockGetIncorpUpdate(transId, Some(incorpUpdate))
        mockInsertSubscription(sub, upsertResult)
        mockUpsertToQueue(false)

        val result = await(service.checkForSubscription(transId, regime, subscriber, "www.test.com", forced = true))

        result mustBe FailedSub
      }
    }
  }

  "addSubscription" must {

    "return a SuccessfulSub when a new subscription has been added" in new Setup {
       val ur: UpsertResult = UpsertResult(0, 0, Seq())
      when(mockSubRepo.insertSub(sub)).thenReturn(Future(ur))

      val result = await(service.addSubscription(transId, regime, subscriber, url))
      result mustBe SuccessfulSub(false)
    }

    "return a FailedSub when a new subscription fails to be added" in new Setup {
      val errors = Seq(new WriteError(1, "An error occured", BsonDocument()))
      val ur: UpsertResult = UpsertResult(0, 0, errors)
      when(mockSubRepo.insertSub(sub)).thenReturn(Future(ur))

      val result = await(service.addSubscription(transId, regime, subscriber, url))
      result mustBe FailedSub
    }
  }


  "deleteSubscription" must {
    "return a DeletedSub when an existing subscription is deleted" in new Setup {
      val deleteResult = DeleteResult.acknowledged(1)
      when(mockSubRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(deleteResult))

      val result = await(service.deleteSubscription(transId, regime, subscriber))
      result mustBe DeletedSub
    }

    "return a NotDeletedSub when an existing subscription has failed to be deleted" in new Setup {
      val deleteResult = DeleteResult.acknowledged(0)
      when(mockSubRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(deleteResult))

      val result = await(service.deleteSubscription(transId, regime, subscriber))
      result mustBe NotDeletedSub
    }
  }


  "getSubscription" must {

    "return a Subscription when a Subscription exists" in new Setup {
      when(mockSubRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(Some(sub)))

      val result = service.getSubscription(transId, regime, subscriber)
      await(result) mustBe Some(sub)
    }

    "return None when a Subscription does not exist" in new Setup {
      when(mockSubRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(None))

      val result = service.getSubscription(transId, regime, subscriber)
      await(result) mustBe None
    }
  }
}
