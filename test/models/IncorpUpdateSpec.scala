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

package models

import Helpers.{JSONhelpers, SCRSSpec}
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._


class IncorpUpdateSpec extends SCRSSpec with JSONhelpers {

  "writes" should {

    val transactionId = "trans12345"
    val subscriber = "SCRS"
    val regime = "CT"
    val callbackUrl = "www.url.com"
    val crn = "crn12345"
    val incDate = DateTime.parse("2000-12-12")
    val status = "accepted"
    val time =  DateTime.now(DateTimeZone.UTC)

    "return json when an accepted incorporation is provided" in {

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
      """.stripMargin)

      val incorpUpdate = IncorpUpdate(transactionId, status, Some(crn), Some(incDate), "tp", None)
      val response = Json.toJson(IncorpUpdateResponse(regime, subscriber, callbackUrl, incorpUpdate))(IncorpUpdateResponse.writes).as[JsObject]

      val (generatedTS, jsonNoTS) = extractTimestamp(response)

      // TODO - should be an ISO formatted timestamp
      // check it's within a second
      generatedTS shouldBe time.toDate.getTime +- 1000
      jsonNoTS shouldBe json
    }



    "return json when a rejected incorporation is provided" in {
      val json = Json.parse(
        s"""
           |{"SCRSIncorpStatus":{
           |"IncorpSubscriptionKey":{
           |"subscriber":"SCRS",
           |"discriminator":"$regime",
           |"transactionId":"$transactionId"
           |},
           |"SCRSIncorpSubscription":{
           |"callbackUrl":"www.url.com"
           |},
           |"IncorpStatusEvent":{
           |"status":"rejected",
           |"description":"description"}}}
      """.stripMargin)

      val incorpUpdate = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))
      val response = Json.toJson(IncorpUpdateResponse(regime, subscriber, callbackUrl, incorpUpdate))(IncorpUpdateResponse.writes).as[JsObject]

      val (generatedTS, jsonNoTS) = extractTimestamp(response)

      generatedTS shouldBe time.toDate.getTime +- 1000
      jsonNoTS shouldBe json
    }

    "return json including a valid timestamp" in {
      val incorpUpdate = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))

      val before = DateTime.now(DateTimeZone.UTC).getMillis
      val json = Json.toJson(IncorpUpdateResponse(regime, subscriber, callbackUrl, incorpUpdate))(IncorpUpdateResponse.writes).as[JsObject]
      val timestamp = (json \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "timestamp").as[Long]
      val after = DateTime.now(DateTimeZone.UTC).getMillis

      (before <= timestamp && after >= timestamp) shouldBe true
    }
  }

  "mongoFormat" should {
    def j(ts: String) = Json.parse(
      s"""
         |{
         |"_id":"transId1",
         |"transaction_status":"awaiting",
         |"company_number":"08978562",
         |"incorporated_on":"${ts}",
         |"timepoint":"timepoint",
         |"transaction_status_description":"status"
         |}
      """.stripMargin)

    "return an IncorpUpdate in mongo format and be able to convert it back again consistently" in {
      val ts = "2013-12-12"
      val json = j(ts)
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", Some("08978562"), Some(DateTime.parse(ts)), "timepoint", Some("status"))

      val result = json.validate[IncorpUpdate](IncorpUpdate.mongoFormat)

      result shouldBe JsSuccess(incorpUpdate)

      Json.toJson[IncorpUpdate](result.get)(IncorpUpdate.mongoFormat) shouldBe json
    }
  }

  "cohoFormat (and queueFormat)" should {
    def j(ts: String) = Json.parse(
      s"""
         |{
         |"transaction_id":"transId1",
         |"transaction_status":"awaiting",
         |"company_number":"08978562",
         |"incorporated_on":"${ts}",
         |"timepoint":"timepoint",
         |"transaction_status_description":"status"
         |}
      """.stripMargin)

    "return an IncorpUpdate in mongo format and be able to convert it back again consistently" in {
      val ts = "2013-12-12"
      val json = j(ts)
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", Some("08978562"), Some(DateTime.parse(ts)), "timepoint", Some("status"))

      val result = json.validate[IncorpUpdate](IncorpUpdate.cohoFormat)

      result shouldBe JsSuccess(incorpUpdate)

      Json.toJson[IncorpUpdate](result.get)(IncorpUpdate.cohoFormat) shouldBe json
    }
  }


  "responseFormat" should {
    def j(ts: String) = Json.parse(
      s"""
         |{
         |"transaction_id":"transId1",
         |"status":"awaiting",
         |"crn":"08978562",
         |"incorporationDate":"${ts}",
         |"timepoint":"timepoint",
         |"transaction_status_description":"status"
         |}
      """.stripMargin)

    "return an IncorpUpdate in mongo format and be able to convert it back again consistently" in {
      val ts = "2013-12-12"
      val json = j(ts)
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", Some("08978562"), Some(DateTime.parse(ts)), "timepoint", Some("status"))

      val result = json.validate[IncorpUpdate](IncorpUpdate.responseFormat)

      result shouldBe JsSuccess(incorpUpdate)

      Json.toJson[IncorpUpdate](result.get)(IncorpUpdate.responseFormat) shouldBe json
    }
  }
}