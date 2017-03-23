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

package mongo

import Helpers.SCRSSpec
import controllers.SubscriptionController
import models.Subscription
import org.mockito.Mockito._
import repositories.{FailedSub, SubscriptionsRepository, SuccessfulSub}
import services.SubscriptionService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by jackie on 20/03/17.
  */
class SubscriptionsRepositorySpec extends SCRSSpec {

  class Setup {
    val controller = new SubscriptionController {
      val service = mock[SubscriptionService]
    }
    val mockService = mock[SubscriptionService]
    val mockRepo = mock[SubscriptionsRepository]
  }
  val transId = "transID"
  val regime = "CT"
  val subscriber = "test"
  val sub = Subscription("transID", "CT", "test", "url")
  val success = SuccessfulSub
  val failed = FailedSub

  "insertSub" should {
    "return a SuccessfulSub when a subscription has been successfully inserted" in new Setup {
      when(mockRepo.insertSub(sub)).thenReturn(Future.successful(success))

      val result = mockRepo.insertSub(sub)
      await(result) shouldBe success
    }

    "return a FailedSub when a subscription has not been successfully inserted" in new Setup {
      when(mockRepo.insertSub(sub)).thenReturn(Future.successful(failed))

      val result = mockRepo.insertSub(sub)
      await(result) shouldBe failed
    }
  }
}


