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

package models

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsLookupResult, JsObject, Json, __}
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Created by jackie on 23/03/17.
  */
class IncorpUpdateSpec extends UnitSpec {

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

    def extractTimestamp(json: JsObject): (Long, JsObject) = {
      val generatedTS = (json \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "timestamp").as[Long]
      val t = (__ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "timestamp").json.prune
      (generatedTS, (json transform t).get)
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


}
