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
import org.mockito.Matchers
import play.api.libs.json.{JsObject, Json}
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

    "return a 200 and json when a company profile is successfully fetched from TX Api (because it is not incorporated yet)" in new Setup {
      when(mockService.checkIfCompIncorporated(Matchers.any[String])).thenReturn(Future.successful(None))
      when(mockService.fetchCompanyProfileFromTxOrCoho(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(Some(json)))

      val result = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) shouldBe 200
      jsonBodyOf(await(result)) shouldBe json
    }

    "return a 404 when a company profile could not be found by the supplied transaction Id (from transactional api) when company not incorporated" in new Setup {

      when(mockService.fetchCompanyProfileFromTxOrCoho(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(None))

      val result = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) shouldBe 404
    }

    "return a 200 and json when a company profile is successfully fetched from Public API (because it is incorporated)" in new Setup {

      val expected = Json.parse(
        s"""
           |{
           |  "company_name": "MOOO LIMITED",
           |  "type": "ltd",
           |  "company_type": "ltd",
           |  "company_status": "foo",
           |  "registered_office_address": {
           |    "country": "United Kingdom",
           |    "address_line_2": "foo2",
           |    "premises": "prem1",
           |    "postal_code": "post1",
           |    "address_line_1": "address1",
           |    "locality": "locality1"
           |  },
           |
             |   "sic_codes": [
           |    {
           |      "sic_description": "",
           |      "sic_code": "84240"
           |    },
           |    {
           |      "sic_description": "",
           |      "sic_code": "01410"
           |    }
           |  ]
           |             }
    """.stripMargin).as[JsObject]

      when(mockService.checkIfCompIncorporated(Matchers.any[String])).thenReturn(Future.successful(Some("")))
      when(mockService.transformDataFromCoho(Matchers.any[JsObject])).thenReturn(Future.successful(Some(expected)))
      when(mockService.fetchCompanyProfileFromTxOrCoho(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(Some(json)))

      val result = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) shouldBe 200
      jsonBodyOf(await(result)) shouldBe json
    }

    "return a 200 when the company appears incoporated in INCORP_INFO and a call is made to public API, but it cannot find this record, the service then makes the call to tx api successfully returning the json" in new Setup {
      when(mockService.checkIfCompIncorporated(Matchers.any[String])).thenReturn(Future.successful(Some("")))
      when(mockService.fetchCompanyProfileFromCoho(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(None))
      when(mockService.fetchCompanyProfileFromTx(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(Some(json)))
      when(mockService.fetchCompanyProfileFromTxOrCoho(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(Some(json)))

      val result = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) shouldBe 200
    }

    "return a 404 when the company appears incoporated in INCORP_INFO and a call is made to public API, but it cannot find this record, the service then makes the call to tx api whereby the record cannot be found" in new Setup {
      when(mockService.checkIfCompIncorporated(Matchers.any[String])).thenReturn(Future.successful(Some("")))
      when(mockService.fetchCompanyProfileFromCoho(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(None))
      when(mockService.fetchCompanyProfileFromTx(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(None))
      when(mockService.fetchCompanyProfileFromTxOrCoho(eqTo(transactionId))(any()))
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
