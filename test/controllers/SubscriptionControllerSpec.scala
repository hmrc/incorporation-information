/*
 * Copyright 2019 HM Revenue & Customs
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

import Helpers.{JSONhelpers, SCRSSpec}
import models.{IncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories._
import services.SubscriptionService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionControllerSpec extends SCRSSpec with JSONhelpers {

  val mockService = mock[SubscriptionService]

  class Setup {
    val controller = new SubscriptionController {
      override val service = mockService
    }
  }

  val transactionId = "trans-12345"
  val regime = "CT"
  val subscriber = "subscriber1"
  val callbackUrl = "www.url.com"
  val crn = "crn12345"
  val status = "accepted"
  val incDate = DateTime.parse("2000-12-12")
  val sub = Subscription(transactionId, regime, subscriber, "www.test.com")
  val testIncorpUpdate = IncorpUpdate(transactionId, status, Some(crn), Some(incDate), "20170327093004787", None)

  "check Subscription" should {

    val requestBody = Json.parse(
      s"""
         |{
         | "SCRSIncorpSubscription":{
         |   "callbackUrl":"$callbackUrl"
         | }
         |}
         |""".stripMargin)

    val json = Json.parse(
     s"""
        |{
        |  "SCRSIncorpStatus":{
        |    "IncorpSubscriptionKey":{
        |      "subscriber":"$subscriber",
        |      "discriminator":"$regime",
        |      "transactionId":"$transactionId"
        |    },
        |    "SCRSIncorpSubscription":{
        |      "callbackUrl":"$callbackUrl"
        |    },
        |    "IncorpStatusEvent":{
        |      "status":"$status",
        |      "crn":"$crn",
        |      "incorporationDate":${incDate.getMillis}
        |    }
        |  }
        |}
        |""".stripMargin)

    "return a 200(Ok) when an incorp update is returned for an existing subscription" in new Setup {
      when(mockService.checkForSubscription(eqTo(transactionId), any(),any(),any(), any())(any()))
        .thenReturn(Future(IncorpExists(testIncorpUpdate)))

      val response = FakeRequest().withBody(requestBody)

      val fResult = call(controller.checkSubscription(transactionId,regime,subscriber), response)
      val result = await(fResult)

      val (generatedTS, jsonNoTS) = extractTimestamp(jsonBodyOf(result).as[JsObject])

      status(result) shouldBe 200
      jsonNoTS shouldBe json
    }

    "return a 202(Accepted) when a new subscription is created" in new Setup {
      when(mockService.checkForSubscription(eqTo(transactionId), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(SuccessfulSub()))

      val response = FakeRequest().withBody(requestBody)

      val fResult = call(controller.checkSubscription(transactionId, regime, subscriber), response)
      val result = await(fResult)

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

    "return a 404 when a subscription cannot be found" in new Setup {
      when(mockService.deleteSubscription(Matchers.eq(transactionId),Matchers.eq(regime),Matchers.eq(subscriber)))
        .thenReturn(Future.successful(NotDeletedSub))

      val response = FakeRequest()

      val result = call(controller.removeSubscription(transactionId,regime,subscriber), response)
      status(result) shouldBe 404
    }
  }

  "getSubscription" should {
    "return a 200 when a subscription is found" in new Setup {
      when(mockService.getSubscription(Matchers.eq(transactionId),Matchers.eq(regime),Matchers.eq(subscriber)))
        .thenReturn(Future.successful(Some(sub)))

      val response = FakeRequest()

      val result = call(controller.getSubscription(transactionId,regime,subscriber), response)
      status(result) shouldBe 200
      result.map(res => jsonBodyOf(res) shouldBe Json.toJson(sub))
    }

    "return a 404 when a subscription is found" in new Setup {
      when(mockService.getSubscription(Matchers.eq(transactionId),Matchers.eq(regime),Matchers.eq(subscriber)))
        .thenReturn(Future.successful(None))

      val response = FakeRequest()

      val result = call(controller.getSubscription(transactionId,regime,subscriber), response)
      status(result) shouldBe 404
    }
  }

}
