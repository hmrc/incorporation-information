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

package controllers.test

import Helpers.SCRSSpec
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.call

class CallbackTestEndpointControllerSpec extends SCRSSpec {

  "post" should {

    "return a 200" in {
      val controller = new CallbackTestEndpointController {}
     // val res = await(controller.post(FakeRequest()))
      val request = FakeRequest().withBody[JsObject](Json.parse("""{"test":"tesste"}""").as[JsObject])
      val res = call(controller.post,request)
      status(res) shouldBe 200
    }
  }

}