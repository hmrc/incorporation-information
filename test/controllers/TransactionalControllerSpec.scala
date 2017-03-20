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
import play.api.libs.json.Json
import play.api.test.FakeRequest
import services.TransactionalService
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}

import scala.concurrent.Future

class TransactionalControllerSpec extends SCRSSpec {

  val mockService = mock[TransactionalService]

  class Setup {
    val controller = new TransactionalController {
      override val service = mockService
    }
  }

  val transactionId = "trans-12345"
  val officerId = "off-12345"

  "fetchCompanyProfile" should {

    val json = Json.parse("""{"test":"json"}""")

    "return a 200 and a json when a company profile is successfully fetched" in new Setup {
      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(Some(json)))

      val result = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) shouldBe 200
      jsonBodyOf(await(result)) shouldBe json
    }

    "return a 404 when a company profile could not be found by the supplied transaction Id" in new Setup {
      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(None))

      val result = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) shouldBe 404
    }
  }

  "fetchOfficerList" should {

    val json = Json.parse("""{"test":"json"}""")

    "return a 200 and a json when an officer list is successfully fetched" in new Setup {
      when(mockService.fetchOfficerList(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(Some(json)))

      val result = controller.fetchOfficerList(transactionId)(FakeRequest())
      status(result) shouldBe 200
      jsonBodyOf(await(result)) shouldBe json
    }

    "return a 404 when an officer list could not be found by the supplied transaction Id" in new Setup {
      when(mockService.fetchOfficerList(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(None))

      val result = controller.fetchOfficerList(transactionId)(FakeRequest())
      status(result) shouldBe 404
    }
  }
}
