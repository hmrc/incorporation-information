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

package services

import Helpers.SCRSSpec
import connectors.{IncorporationAPIConnector, FailedTransactionalAPIResponse, SuccessfulTransactionalAPIResponse}
import play.api.libs.json.{JsValue, JsObject, JsPath, Json}
import org.mockito.Mockito._

import scala.concurrent.Future

class TransactionalServiceSpec extends SCRSSpec {

  val mockConnector = mock[IncorporationAPIConnector]

  class Setup {
    val service = new TransactionalService {
      override protected val connector = mockConnector
    }
  }

  def buildJson(txId: String = "000-033808") = Json.parse(
    s"""
      |{
      |  "transaction_id": "$txId",
      |  "company_name": "MOOO LIMITED",
      |  "company_type": "ltd",
      |  "registered_office_address": {
      |    "country": "United Kingdom",
      |    "address_line_2": "GORING-BY-SEA",
      |    "premises": "98",
      |    "postal_code": "BN12 6AG",
      |    "address_line_1": "LIMBRICK LANE",
      |    "locality": "WORTHING"
      |  },
      |  "officers": [
      |    {
      |      "date_of_birth": {
      |        "month": "11",
      |        "day": "12",
      |        "year": "1973"
      |      },
      |      "name_elements": {
      |        "forename": "Bob",
      |        "surname": "Bobbings",
      |        "other_forenames": "Bimbly Bobblous"
      |      },
      |      "address": {
      |        "country": "United Kingdom",
      |        "address_line_2": "GORING-BY-SEA",
      |        "premises": "98",
      |        "postal_code": "BN12 6AG",
      |        "address_line_1": "LIMBRICK LANE",
      |        "locality": "WORTHING"
      |      }
      |    },
      |    {
      |      "date_of_birth": {
      |        "month": "07",
      |        "day": "12",
      |        "year": "1988"
      |      },
      |      "name_elements": {
      |        "title": "Mx",
      |        "forename": "Jingly",
      |        "surname": "Jingles"
      |      },
      |      "address": {
      |        "country": "England",
      |        "premises": "713",
      |        "postal_code": "NE1 4BB",
      |        "address_line_1": "ST. JAMES GATE",
      |        "locality": "NEWCASTLE UPON TYNE"
      |      }
      |    }
      |  ],
      |  "sic_codes": [
      |    {
      |      "sic_description": "Public order and safety activities",
      |      "sic_code": "84240"
      |    },
      |    {
      |      "sic_description": "Raising of dairy cattle",
      |      "sic_code": "01410"
      |    }
      |  ]
      |}
    """.stripMargin)

  val officersJson = (buildJson() \ "officers").get
  val companyProfileJson = (buildJson().as[JsObject] - "officers").as[JsValue]

  val incorrectJson = Json.parse(
    """
      |{
      | "incorrect":"json"
      |}
    """.stripMargin)

  "extractJson" should {

    "fetch a json sub-document from a successful TransactionalAPIResponse by the supplied key" in new Setup {
      val json = buildJson()
      val transformer = (JsPath \ "officers").json.prune

      val response = SuccessfulTransactionalAPIResponse(json)

      await(service.extractJson(response, transformer)) shouldBe Some(companyProfileJson)
    }

    "return a None when attempting to fetch a sub-document from a successful TransactionalAPIResponse with an un-matched transformer key" in new Setup {
      val json = buildJson()
      val transformer = (JsPath \ "unmatched").json.prune

      val response = SuccessfulTransactionalAPIResponse(json)

      await(service.extractJson(response, transformer)) shouldBe None
    }

    "return a None when a FailedTransactionalAPIResponse is returned" in new Setup {
      val response = FailedTransactionalAPIResponse
      val transformer = (JsPath \ "officers").json.prune

      await(service.extractJson(response, transformer)) shouldBe None
    }
  }

  "fetchCompanyProfile" should {

    val transactionId = "12345"

    "return some Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(buildJson())
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))
      await(service.fetchCompanyProfile(transactionId)) shouldBe Some(companyProfileJson)
    }

    "return None" when {

      "a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
        val response = FailedTransactionalAPIResponse
        when(mockConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchCompanyProfile(transactionId)) shouldBe None
      }

//      "a SuccessfulTransactionalAPIResponse is returned for the supplied transaction Id but an incorrect json document is provided" in new Setup {
//        val response = SuccessfulTransactionalAPIResponse(incorrectJson)
//        when(mockTransactionalConnector.fetchTransactionalData(Matchers.any())(Matchers.any()))
//          .thenReturn(Future.successful(response))
//        await(service.fetchCompanyProfile(transactionId)) shouldBe None
//      }
    }
  }

  "fetchOfficerList" should {

    val transactionId = "12345"

    "return some Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(buildJson())
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))
      await(service.fetchOfficerList(transactionId)) shouldBe Some(officersJson)
    }

    "return None" when {

      "a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
        val response = FailedTransactionalAPIResponse
        when(mockConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchOfficerList(transactionId)) shouldBe None
      }

      "a SuccessfulTransactionalAPIResponse is returned for the supplied transaction Id but an incorrect json document is provided" in new Setup {
        val response = SuccessfulTransactionalAPIResponse(incorrectJson)
        when(mockConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchOfficerList(transactionId)) shouldBe None
      }
    }
  }
}
