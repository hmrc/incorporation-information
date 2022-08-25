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

package controllers

import Helpers.{JSONhelpers, SCRSSpec}
import models.{IncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories._
import services.SubscriptionService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionControllerSpec extends SCRSSpec with JSONhelpers {

  val mockService: SubscriptionService = mock[SubscriptionService]

  class Setup {
    val controller: SubscriptionController = new SubscriptionController {
      override val service: SubscriptionService = mockService
      val controllerComponents: ControllerComponents = stubControllerComponents()
      implicit val ec: ExecutionContext = global
    }
  }

  val transactionId = "trans-12345"
  val regime = "CT"
  val subscriber = "subscriber1"
  val callbackUrl = "www.url.com"
  val crn = "crn12345"
  val acceptedStatus = "accepted"
  val incDate: DateTime = DateTime.parse("2000-12-12")
  val sub: Subscription = Subscription(transactionId, regime, subscriber, "www.test.com")
  val testIncorpUpdate: IncorpUpdate = IncorpUpdate(transactionId, acceptedStatus, Some(crn), Some(incDate), "20170327093004787", None)

  "check Subscription" must {

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
        |      "status":"$acceptedStatus",
        |      "crn":"$crn",
        |      "incorporationDate":${incDate.getMillis}
        |    }
        |  }
        |}
        |""".stripMargin)

    "return a 200(Ok) when an incorp update is returned for an existing subscription" in new Setup {
      when(mockService.checkForSubscription(eqTo(transactionId), any(),any(),any(), any()))
        .thenReturn(Future(IncorpExists(testIncorpUpdate)))

      val request: FakeRequest[JsValue] = FakeRequest().withBody(requestBody)

      val result: Future[Result] = controller.checkSubscription(transactionId,regime,subscriber)(request)

      val (generatedTS, jsonNoTS) = extractTimestamp(contentAsJson(result).as[JsObject])

      status(result) mustBe 200
      jsonNoTS mustBe json
    }

    "return a 202(Accepted) when a new subscription is created" in new Setup {
      when(mockService.checkForSubscription(eqTo(transactionId), any(), any(), any(), any()))
        .thenReturn(Future.successful(SuccessfulSub()))

      val request: FakeRequest[JsValue] = FakeRequest().withBody(requestBody)

      val result: Future[Result] = controller.checkSubscription(transactionId, regime, subscriber)(request)

      status(result) mustBe 202
    }
  }


  "Remove subscription" must {
    "return a 200 when a subscription is deleted" in new Setup {
      when(mockService.deleteSubscription(ArgumentMatchers.eq(transactionId),ArgumentMatchers.eq(regime),ArgumentMatchers.eq(subscriber)))
        .thenReturn(Future.successful(DeletedSub))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

      val result: Future[Result] = controller.removeSubscription(transactionId,regime,subscriber)(request)
      status(result) mustBe 200
    }

    "return a 404 when a subscription cannot be found" in new Setup {
      when(mockService.deleteSubscription(ArgumentMatchers.eq(transactionId),ArgumentMatchers.eq(regime),ArgumentMatchers.eq(subscriber)))
        .thenReturn(Future.successful(NotDeletedSub))

      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

      val result: Future[Result] = controller.removeSubscription(transactionId,regime,subscriber)(request)
      status(result) mustBe 404
    }
  }

}
