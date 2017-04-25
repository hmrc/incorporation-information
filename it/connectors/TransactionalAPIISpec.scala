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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpecBase
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication

class TransactionalAPIISpec extends IntegrationSpecBase {

  //todo: set feature switch to false
  override implicit lazy val app = FakeApplication(additionalConfiguration = Map(
    "microservice.services.incorp-update-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/incorporation-frontend-stubs",
    "microservice.services.incorp-update-api.url" -> s"http://${wiremockHost}:${wiremockPort}"
  ))

  "fetchTransactionalData" should {

    val transactionId = "12345"

    val destinationUrl = s"/incorporation-frontend-stubs/fetch-data/$transactionId"

    val body =
      s"""
         |{
         |  "transaction_id": "000-033808",
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
    """.stripMargin

    // TODO - re-check against expecations
    "return a 200 and Json from the companies house API stub" in {
      stubGet(destinationUrl, 200, body)

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200
      response.body shouldBe (Json.parse(body).as[JsObject] - "officers").toString()
    }

    "return a 404 if a Json body cannot be returned for the given transaction Id" in {
      stubGet(destinationUrl, 404, "")

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 404
    }
  }
}
