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
import models.{IncorpUpdate, Subscription}
import repositories._
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo, _}
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by jackie on 29/03/17.
  */
class SubscriptionServiceSpec extends SCRSSpec {

  val mockSubRepo = mock[SubscriptionsMongoRepository]
  val mockIncorpRepo = mock[IncorpUpdateMongoRepository]

  trait Setup {

    val service = new SubscriptionService {
      override val subRepo = new SubscriptionsMongo(reactiveMongoComponent) {
        override val repo = mockSubRepo
      }
      override val incorpRepo = new IncorpUpdateMongo(reactiveMongoComponent){
        override val repo = mockIncorpRepo
      }
    }
  }

  val transId = "transId123"
  val incorpUpdate = IncorpUpdate(transId, "Accepted", Some("123456"), None, "timepoint", None)
  val regime = "CT100"
  val subscriber = "abc123"
  val url = "www.test.com"
  val sub = Subscription(transId, regime, subscriber, url)


  "checkForSubscription" should {

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


  "addSubscription" should {
    "return a SuccessfulSub when a new subscription has been added" in new Setup {
       val ur: UpsertResult = UpsertResult(0, 0, Seq())
      when(mockSubRepo.insertSub(sub)).thenReturn(Future(ur))

      val result = await(service.addSubscription(transId, regime, subscriber, url))
      result shouldBe SuccessfulSub
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
      result.map(res => res == sub)
    }

    "return None when a Subscription does not exist" in new Setup {
      when(mockSubRepo.getSubscription(transId, regime, subscriber)).thenReturn(Future(None))

      val result = service.getSubscription(transId, regime, subscriber)
      result.map(res => res == None)
    }
  }


}
