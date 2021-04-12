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

package repositories

import Helpers.SCRSSpec
import models.Subscription
import org.mockito.Mockito._
import play.api.test.Helpers._
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionsRepositorySpec extends SCRSSpec {

  class Setup {
    val mockRepo = mock[SubscriptionsRepository]
  }

  val transId = "transID"
  val regime = "CT"
  val subscriber = "test"
  val sub = Subscription("transID", "CT", "test", "url")
  val success = UpsertResult(1, 0, Seq())
  val failed = UpsertResult(0, 0, Seq(WriteError(0, 11000, "Error the subscription has not been saved")))

  "insertSub" should {
    "return an upsert result with a 1 value for upserted, when a subscription has been successfully inserted" in new Setup {
      val upsertResult = UpsertResult(0, 1, Seq())
      when(mockRepo.insertSub(sub)).thenReturn(Future.successful(upsertResult))

      val result = await(mockRepo.insertSub(sub))
      result shouldBe upsertResult
    }

    "return an upsert result with a 0 value for upserted, when a subscription has not been successfully inserted" in new Setup {
      val upsertResult = UpsertResult(0, 0, Seq())
      when(mockRepo.insertSub(sub)).thenReturn(Future.successful(upsertResult))

      val result = await(mockRepo.insertSub(sub))
      result shouldBe upsertResult
    }
  }


  "deleteSub" should {
    "return a DeletedSub when a subscription has been deleted" in new Setup {
      val writeResult = DefaultWriteResult(true, 1, Seq(), None, None, None).flatten
      when(mockRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(writeResult))

      val result = await(mockRepo.deleteSub(transId, regime, subscriber))
      result shouldBe writeResult
    }

    "return a FailedSub when a subscription has not been successfully deleted" in new Setup {
      val writeResult = DefaultWriteResult(false, 1, Seq(), None, None, None).flatten
      when(mockRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(writeResult))

      val result = await(mockRepo.deleteSub(transId, regime, subscriber))
      result shouldBe writeResult
    }
  }

  "getSubscription" should {
    "return a Subscription when a subscription exists" in new Setup {
      when(mockRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(Some(sub)))

      val result = await(mockRepo.getSubscription(transId, regime, subscriber))
      result.get shouldBe sub
    }

    "return None when a subscription does not exist" in new Setup {
      when(mockRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(None))

      val result = await(mockRepo.getSubscription(transId, regime, subscriber))
      result shouldBe None
    }
  }
}


