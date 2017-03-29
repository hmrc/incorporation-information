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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import Helpers.SCRSSpec
import models.IncorpUpdate
import org.joda.time.DateTime
import org.mockito.Matchers
import services.SubscriptionService
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.internal.matchers.Any
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.json.Json
import repositories.{DeletedSub, IncorpExists, SuccessfulSub}

import scala.concurrent.Future



class SubscriptionControllerImplSpec extends SCRSSpec {

  implicit val system = ActorSystem("II")
  implicit val materializer = ActorMaterializer()


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

     val json = Json.parse(
       """
         |{
         |  "SCRSIncorpSubscription": {
         |    "callbackUrl": "www.test.com"
         |  }
         |}
       """.stripMargin)

      "return a 202 when new subscription is created" in new Setup {
        when(mockService.checkForSubscription(transactionId,regime,subscriber,"Callback URL"))
          .thenReturn(Future.successful(IncorpExists(testIncorpUpdate)))

        val response = FakeRequest().withBody(json)

        val result = call(controller.checkSubscription(transactionId,regime,subscriber), response)
        status(result) shouldBe 202
      }
   }

  "Remove subscription" should {
    "return a 200 when a subscription is deleted" in new Setup {
      when(mockService.deleteSubscription(Matchers.eq(transactionId),Matchers.eq(regime),Matchers.eq(subscriber)))
        .thenReturn(Future.successful(DeletedSub))

      val response = FakeRequest()

      val result = call(controller.removeSubscription(transactionId,regime,subscriber), response)
      status(result) shouldBe 200
    }
  }

}
