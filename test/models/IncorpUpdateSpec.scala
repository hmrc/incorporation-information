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

import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
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

    "return json when an accepted incorporation is provided" in {
      val crn = "crn12345"
      val incDate = DateTime.parse("2000-12-12")
      val status = "accepted"

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
           |      "incorporationDate":${incDate.getMillis},
           |      "timestamp":"2017-12-21T10:13:09.429Z"
           |    }
           |  }
           |}
      """.stripMargin)

      val incorpUpdate = IncorpUpdate(transactionId, status, Some(crn), Some(incDate), "tp", None)
      val response = Json.toJson(IncorpUpdateResponse(regime, subscriber, callbackUrl, incorpUpdate))(IncorpUpdateResponse.writes)
      response shouldBe json
    }

//    "return json when a rejected incorporation is provided" in {
//      val json = Json.parse(
//        s"""
//           |{"SCRSIncorpStatus":{
//           |"IncorpSubscriptionKey":{
//           |"subscriber":"SCRS",
//           |"discriminator":"PAYE",
//           |"transactionId":"$transactionId"
//           |},
//           |"SCRSIncorpSubscription":{
//           |"callbackUrl":"www.url.com"
//           |},
//           |"IncorpStatusEvent":{
//           |"status":"rejected",
//           |"description":"description",
//           |"timestamp":"2017-12-21T10:13:09.429Z"}}}
//      """.stripMargin)
//
//      val response = Json.toJson(IncorpUpdate("transID", "rejected", None, None, "tp", Some("description")))(IncorpUpdate.writes("www.url.com", transactionId, subscriber, regime))
//      response shouldBe json
//    }

  }
}
