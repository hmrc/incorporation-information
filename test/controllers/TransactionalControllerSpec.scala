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

package controllers

import Helpers.SCRSSpec
import connectors.PublicCohoApiConnector
import models.IncorpUpdate
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.IncorpUpdateRepository
import services.{FailedToFetchOfficerListFromTxAPI, TransactionalService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.TimestampFormats

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class TransactionalControllerSpec extends SCRSSpec {

  val mockService: TransactionalService = mock[TransactionalService]
  val mockIncorpUpdateRepository: IncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockApiConnector: PublicCohoApiConnector = mock[PublicCohoApiConnector]

  class Setup {
    val controller: TransactionalController = new TransactionalController {
      override val service: TransactionalService = mockService
      override val publicApiConnector: PublicCohoApiConnector = mockApiConnector
      override val allowlistedServices = Set("test", "services")
      override val incorpRepo: IncorpUpdateRepository = mockIncorpUpdateRepository
      val controllerComponents: ControllerComponents = stubControllerComponents()
      implicit val ec: ExecutionContext = global
    }
  }

  val transactionId = "trans-12345"
  val crn = "some-crn"
  val officerId = "off-12345"

  "fetchCompanyProfile" must {

    val json = Json.parse("""{"test":"json"}""")

    "return a 200 and json when a company profile is successfully fetched from TX Api (because it is not incorporated yet)" in new Setup {
      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(Some(json)))

      val result: Future[Result] = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) mustBe 200
      contentAsJson(result) mustBe json
    }

    "return a 404 when a company profile could not be found by the supplied transaction Id (from transactional api) when company not incorporated" in new Setup {

      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Future[Result] = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) mustBe 404
    }

    "return a 200 and json when a company profile is successfully fetched from Public API (because it is incorporated)" in new Setup {

      val expected: JsObject = Json.parse(
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

      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(Some(json)))

      val result: Future[Result] = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) mustBe 200
      contentAsJson(result) mustBe json
    }

    "return a 200 when a company profile is fetched" in new Setup {
      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(Some(json)))

      val result: Future[Result] = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) mustBe 200
    }

    "return a 404 when a company profile cannot be found using the supplied txID" in new Setup {
      when(mockService.fetchCompanyProfile(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Future[Result] = controller.fetchCompanyProfile(transactionId)(FakeRequest())
      status(result) mustBe 404
    }

  }

  "fetchIncorporatedCompanyProfile" must {
    val crn = "01234567"
    val json = Json.parse("""{"example":"response"}""")
    "return a 200 with JSON" when {
      "called by an allowlisted service" in new Setup {
        when(mockApiConnector.getCompanyProfile(eqTo(crn), eqTo(true))(any()))
          .thenReturn(Future.successful(Some(json)))

        val result: Future[Result] = controller.fetchIncorporatedCompanyProfile(crn)(FakeRequest().withHeaders("User-Agent" -> "test"))
        status(result) mustBe 200
        contentAsJson(result) mustBe json
      }
      "called by a non-allowlisted service" in new Setup {
        when(mockApiConnector.getCompanyProfile(eqTo(crn), eqTo(false))(any()))
          .thenReturn(Future.successful(Some(json)))

        val result: Future[Result] = controller.fetchIncorporatedCompanyProfile(crn)(FakeRequest().withHeaders("User-Agent" -> "not allowlisted"))
        status(result) mustBe 200
        contentAsJson(result) mustBe json
      }
      "called by an unidentifiable service" in new Setup {
        when(mockApiConnector.getCompanyProfile(eqTo(crn), eqTo(false))(any()))
          .thenReturn(Future.successful(Some(json)))

        val result: Future[Result] = controller.fetchIncorporatedCompanyProfile(crn)(FakeRequest())
        status(result) mustBe 200
        contentAsJson(result) mustBe json
      }
    }
    "return a 404" when {
      "CRN does not exist on Companies House" in new Setup {
        when(mockApiConnector.getCompanyProfile(eqTo(crn), eqTo(true))(any()))
          .thenReturn(Future.successful(None))

        val result: Future[Result] = controller.fetchIncorporatedCompanyProfile(crn)(FakeRequest().withHeaders("User-Agent" -> "test"))
        status(result) mustBe 404
      }
    }
  }

  "fetchOfficerList" must {

    val officerJson = Json.parse(
      """
        |{
        |   "officers":[
        |      {
        |         "name_elements":{
        |            "surname":"testSurname",
        |            "title":"Ms",
        |            "forename":"test"
        |         },
        |         "appointment_link":"/officers/testJson1/appointments",
        |         "officer_role":"director",
        |         "address":{
        |            "country":"United Kingdom",
        |            "premises":"Suite 1111",
        |            "address_line_1":"test address line 1",
        |            "locality":"London",
        |            "postal_code":"AA1A 8AA"
        |         },
        |         "date_of_birth":{
        |            "year":1979,
        |            "month":7
        |         }
        |      },
        |      {
        |         "name_elements":{
        |            "title":"Mr",
        |            "surname":"test",
        |            "forename":"testFore"
        |         },
        |         "appointment_link":"/officers/testJson2/appointments",
        |         "officer_role":"director",
        |         "address":{
        |            "country":"United Kingdom",
        |            "premises":"Suite 1111",
        |            "address_line_1":"address line 1",
        |            "locality":"London",
        |            "postal_code":"AA1A 8AA"
        |         },
        |         "date_of_birth":{
        |            "year":1990,
        |            "month":10
        |         }
        |      }
        |   ]
        |}
        |""".stripMargin)


    "return a 200 and a json when an officer list is successfully fetched" in new Setup {
      when(mockService.fetchOfficerList(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(officerJson))

      val result: Future[Result] = controller.fetchOfficerList(transactionId)(FakeRequest())
      status(result) mustBe 200
      contentAsJson(result) mustBe officerJson
    }

    "return a 404 when an officer list could not be found by the supplied transaction Id" in new Setup {
      when(mockService.fetchOfficerList(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.failed(new FailedToFetchOfficerListFromTxAPI()))

      val result: Future[Result] = controller.fetchOfficerList(transactionId)(FakeRequest())
      status(result) mustBe 404
    }

    "return a 500 when an unknown exception is caught" in new Setup {
      when(mockService.fetchOfficerList(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.failed(new Exception()))

      val result: Future[Result] = controller.fetchOfficerList(transactionId)(FakeRequest())
      status(result) mustBe 500
    }

    "return a 204 when officer list Json is empty" in new Setup {
      val json = Json.parse("""{"officers":[]}""")

      when(mockService.fetchOfficerList(eqTo(transactionId))(any(), any[ExecutionContext]))
        .thenReturn(Future.successful(json))

      val result: Future[Result] = controller.fetchOfficerList(transactionId)(FakeRequest())
      status(result) mustBe 204
    }
  }

  "fetchIncorpUpdate" must {
    "return a 200 and the CRN as JSON" in new Setup {
      val incorpUpdate: IncorpUpdate = IncorpUpdate(transactionId, "accepted", Some("example CRN"), Some(LocalDateTime.parse("2018-05-01", TimestampFormats.ldtFormatter)), "", None)
      val expectedJson: JsValue = Json.parse(
        s"""
           |{
           |  "transaction_id": "$transactionId",
           |  "status": "accepted",
           |  "crn": "example CRN",
           |  "incorporationDate": "2018-05-01",
           |  "timepoint": ""
           |}
         """.stripMargin)

      when(mockIncorpUpdateRepository.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(Future.successful(Some(incorpUpdate)))

      val result: Future[Result] = controller.fetchIncorpUpdate(transactionId)(FakeRequest())

      status(result) mustBe 200
      contentAsJson(result) mustBe expectedJson
    }
    "return a 204" in new Setup {
      when(mockIncorpUpdateRepository.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(Future.successful(None))

      val result: Future[Result] = controller.fetchIncorpUpdate(transactionId)(FakeRequest())
      status(result) mustBe 204
    }
  }

  "fetchSicCodesByTransactionID" must {
    val sicJson = Json.parse("""{"sic_codes": ["12345", "23456"]}""")

    "return sic codes from the CH API using TxId" in new Setup {
      when(mockService.fetchSicByTransId(eqTo(transactionId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(sicJson)))

      val result: Future[Result] = controller.fetchSicCodesByTransactionID(transactionId)(FakeRequest())
      status(result) mustBe 200
      contentAsJson(result) mustBe sicJson
    }

    "return no content when no data returned from the CH API using TxId" in new Setup {
      when(mockService.fetchSicByTransId(eqTo(transactionId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Future[Result] = controller.fetchSicCodesByTransactionID(transactionId)(FakeRequest())
      status(result) mustBe 204
    }
  }

  "fetchSicCodesByCRN" must {
    val sicJson = Json.parse("""{"sic_codes": ["12345", "23456"]}""")

    "return sic codes from the CH API" in new Setup {
      when(mockService.fetchSicByCRN(eqTo(crn))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(sicJson)))

      val result: Future[Result] = controller.fetchSicCodesByCRN(crn)(FakeRequest())
      status(result) mustBe 200
      contentAsJson(result) mustBe sicJson
    }

    "return no content when no data returned from the CH API" in new Setup {
      when(mockService.fetchSicByCRN(eqTo(crn))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      val result: Future[Result] = controller.fetchSicCodesByCRN(crn)(FakeRequest())
      status(result) mustBe 204
    }
  }
  "fetchShareholders" must {
    "return 204 if array is empty" in new Setup {
      when(mockService.fetchShareholders(eqTo(transactionId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Json.arr())))
      val res: Future[Result] = controller.fetchShareholders(transactionId)(FakeRequest())
      status(res) mustBe 204
    }

    "return 200 if array is returned and size > 0" in new Setup {
      when(mockService.fetchShareholders(eqTo(transactionId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Json.arr(Json.obj("foo" -> "bar")))))
      val result: Future[Result] = controller.fetchShareholders(transactionId)(FakeRequest())
      status(result) mustBe 200
      contentAsJson(result) mustBe Json.parse(
        """[
          | {
          |   "foo": "bar"
          | }
          |]
        """.stripMargin)

    }
    "return 404 if key does not exist in the json and service returned None" in new Setup {
      when(mockService.fetchShareholders(eqTo(transactionId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      val res: Future[Result] = controller.fetchShareholders(transactionId)(FakeRequest())
      status(res) mustBe 404
    }
  }
}