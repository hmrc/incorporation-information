/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json._
import utils.{DateCalculators, TimestampFormats}

import java.time.{Instant, LocalDateTime, ZoneOffset}


class IncorpUpdateSpec extends SCRSSpec with JSONhelpers with DateCalculators {

  "writes" must {

    val transactionId = "trans12345"
    val subscriber = "SCRS"
    val regime = "CT"
    val callbackUrl = "www.url.com"
    val crn = "crn12345"
    val incDate = LocalDateTime.parse("2000-12-12", TimestampFormats.ldtFormatter)
    val status = "accepted"
    val time =  getDateTimeNowUTC

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
           |      "incorporationDate":${incDate.toInstant(ZoneOffset.UTC).toEpochMilli}
           |    }
           |  }
           |}
      """.stripMargin)

      val incorpUpdate = IncorpUpdate(transactionId, status, Some(crn), Some(incDate), "tp", None)
      val response = Json.toJson(IncorpUpdateResponse(regime, subscriber, callbackUrl, incorpUpdate))(IncorpUpdateResponse.writes).as[JsObject]

      val (generatedTS, jsonNoTS) = extractTimestamp(response)

      generatedTS mustBe time.toInstant(ZoneOffset.UTC).toEpochMilli +- 1000
      jsonNoTS mustBe json
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

      generatedTS mustBe time.toInstant(ZoneOffset.UTC).toEpochMilli +- 1000
      jsonNoTS mustBe json
    }

    "return json including a valid timestamp" in {
      val incorpUpdate = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))

      val before = Instant.now().toEpochMilli
      val json = Json.toJson(IncorpUpdateResponse(regime, subscriber, callbackUrl, incorpUpdate))(IncorpUpdateResponse.writes).as[JsObject]
      val timestamp = (json \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "timestamp").as[Long]
      val after = Instant.now().toEpochMilli

      (before <= timestamp && after >= timestamp) mustBe true
    }
  }

  "mongoFormat" must {
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
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", Some("08978562"), Some(LocalDateTime.parse(ts, TimestampFormats.ldtFormatter)), "timepoint", Some("status"))

      val result = json.validate[IncorpUpdate](IncorpUpdate.mongoFormat)

      result mustBe JsSuccess(incorpUpdate)

      Json.toJson[IncorpUpdate](result.get)(IncorpUpdate.mongoFormat) mustBe json
    }
  }

  "cohoFormat (and queueFormat)" must {
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
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", Some("08978562"), Some(LocalDateTime.parse(ts, TimestampFormats.ldtFormatter)), "timepoint", Some("status"))

      val result = json.validate[IncorpUpdate](IncorpUpdate.cohoFormat)

      result mustBe JsSuccess(incorpUpdate)

      Json.toJson[IncorpUpdate](result.get)(IncorpUpdate.cohoFormat) mustBe json
    }
  }


  "responseFormat" must {
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
      val incorpUpdate = IncorpUpdate("transId1", "awaiting", Some("08978562"), Some(LocalDateTime.parse(ts, TimestampFormats.ldtFormatter)), "timepoint", Some("status"))

      val result = json.validate[IncorpUpdate](IncorpUpdate.responseFormat)

      result mustBe JsSuccess(incorpUpdate)

      Json.toJson[IncorpUpdate](result.get)(IncorpUpdate.responseFormat) mustBe json
    }
  }
}