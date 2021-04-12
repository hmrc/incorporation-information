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

import Helpers.{LogCapturing, SCRSSpec}
import play.api.Logger
import play.api.libs.json.{JsArray, Json}

class ShareholdersSpec extends SCRSSpec with LogCapturing{

  "return None if no key exists" in {
    val json =
      Json.parse(
        """
          |{
          |   "foo" : "bar"
          |}
        """.stripMargin)
    json.as[Option[JsArray]](Shareholders.reads).isDefined shouldBe false
  }
  "return None if key exists but is not a JsArray" in {
    val json =
      Json.parse(
        """
          |{
          |   "shareholders" : "bar"
          |}
        """.stripMargin)
    withCaptureOfLoggingFrom(Logger) { logEvents =>
      json.as[Option[JsArray]](Shareholders.reads).isDefined shouldBe false
      val message = "[ShareholdersReads] 'shareholders' is not an array"
      val log =  logEvents.map(l => (l.getLevel, l.getMessage)).head
      log._1.toString shouldBe "ERROR"
      log._2 shouldBe message
    }
  }
  "return Some if key exists and is jsArray" in {
    val json =
      Json.parse(
        """
          |{
          |   "shareholders" : []
          |}
        """.stripMargin)
    json.as[Option[JsArray]](Shareholders.reads).get shouldBe Json.arr()
  }
}
