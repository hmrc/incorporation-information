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

package controllers

import Helpers.SCRSSpec
import models.IncorpUpdate
import org.joda.time.DateTime
import org.mockito.Matchers
import services.SubscriptionService
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.internal.matchers.Any
import play.api.test.FakeRequest
import play.api.libs.json.Json
import repositories.{DeletedSub, IncorpExists, SuccessfulSub}

import scala.concurrent.Future



class SubscriptionControllerImplSpec extends SCRSSpec {


    val mockService = mock[SubscriptionService]

    class Setup {
      val controller = new SubscriptionControllerImpl {

      }
    }


    val transactionId = "trans-12345"
    val regime = "CT"
    val subscriber = "subscriber1"
    val testIncorpUpdate = IncorpUpdate(transactionId,"held",Some("123456789"),None,"20170327093004787",None)

    "check Subscription" should {

//      val json = Json.parse("""{"test":"json"}""")

      "return a 200 when new subscription is created" in new Setup {
        when(mockService.checkForSubscription(transactionId,regime,subscriber,"Callback URL"))
          .thenReturn(Future.successful(IncorpExists(testIncorpUpdate)))

        val result = controller.checkSubscription(transactionId,regime,subscriber)(FakeRequest())
//        status(result) shouldBe OK
//        jsonBodyOf(await(result)) shouldBe json
      }
   }

  "Remove subscription" should {
    "return a 200 when a subscription is deleted" in new Setup {
      when(mockService.deleteSubscription(Matchers.eq(transactionId),Matchers.eq(regime),Matchers.eq(subscriber)))
        .thenReturn(Future.successful(DeletedSub))

      val result = controller.removeSubscription(transactionId,regime,subscriber)
//      status(result) shouldBe Ok("subscription has been deleted")

    }
  }

}
