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

package repositories

import Helpers.SCRSSpec
import com.mongodb.client.result.DeleteResult
import models.Subscription
import org.mockito.Mockito._
import play.api.test.Helpers._

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

  "insertSub" must {
    "return an upsert result with a 1 value for upserted, when a subscription has been successfully inserted" in new Setup {
      val upsertResult = UpsertResult(0, 1, Seq())
      when(mockRepo.insertSub(sub)).thenReturn(Future.successful(upsertResult))

      val result = await(mockRepo.insertSub(sub))
      result mustBe upsertResult
    }

    "return an upsert result with a 0 value for upserted, when a subscription has not been successfully inserted" in new Setup {
      val upsertResult = UpsertResult(0, 0, Seq())
      when(mockRepo.insertSub(sub)).thenReturn(Future.successful(upsertResult))

      val result = await(mockRepo.insertSub(sub))
      result mustBe upsertResult
    }
  }


  "deleteSub" must {
    "return a DeletedSub when a subscription has been deleted" in new Setup {
      val deleteResult = DeleteResult.acknowledged(1)
      when(mockRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(deleteResult))

      val result = await(mockRepo.deleteSub(transId, regime, subscriber))
      result mustBe deleteResult
    }

    "return a FailedSub when a subscription has not been successfully deleted" in new Setup {
      val deleteResult = DeleteResult.acknowledged(0)
      when(mockRepo.deleteSub(transId, regime, subscriber)).thenReturn(Future(deleteResult))

      val result = await(mockRepo.deleteSub(transId, regime, subscriber))
      result mustBe deleteResult
    }
  }

  "getSubscription" must {
    "return a Subscription when a subscription exists" in new Setup {
      when(mockRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(Some(sub)))

      val result = await(mockRepo.getSubscription(transId, regime, subscriber))
      result.get mustBe sub
    }

    "return None when a subscription does not exist" in new Setup {
      when(mockRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(None))

      val result = await(mockRepo.getSubscription(transId, regime, subscriber))
      result mustBe None
    }
  }
}


