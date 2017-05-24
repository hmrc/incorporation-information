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

import helpers.IntegrationSpecBase
import models.IncorpUpdate
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.IncorpUpdateMongo
import scala.concurrent.ExecutionContext.Implicits.global
class TransactionalAPIISpec extends IntegrationSpecBase {

  //todo: set feature switch to false
  override implicit lazy val app = FakeApplication(additionalConfiguration = Map(
    "microservice.services.incorp-update-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/incorporation-frontend-stubs",
    "microservice.services.incorp-update-api.url" -> s"http://${wiremockHost}:${wiremockPort}",
    "microservice.services.public-coho-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/cohoFrontEndStubs"
  ))

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]
  class Setup {
    val incRepo = new IncorpUpdateMongo(reactiveMongoComponent).repo
    incRepo.drop
    def insert(update: IncorpUpdate) = await(incRepo.insert(update))
  }



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
         |    "address_line_2": "add2",
         |    "premises": "98",
         |    "postal_code": "post",
         |    "address_line_1": "lim1",
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
         |        "address_line_2": "add2",
         |        "premises": "98",
         |        "postal_code": "post1",
         |        "address_line_1": "lim1",
         |        "locality": "WORTHING"
         |      }
         |    },
         |    {
         |      "date_of_birth": {
         |        "month": "01",
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
         |        "premises": "prem1",
         |        "postal_code": "post1",
         |        "address_line_1": "add1",
         |        "locality": "local1"
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
    "return a 200 and Json from the companies house API stub" in new Setup {
      stubGet(destinationUrl, 200, body)

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200
      response.body shouldBe (Json.parse(body).as[JsObject] - "officers").toString()
    }

    "return a 404 if a Json body cannot be returned for the given transaction Id" in new Setup {
      stubGet(destinationUrl, 404, "")

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 404
    }

    val input =
      s"""
         |{
         |  "transaction_id": "12345",
         |  "company_number": "crn1",
         |  "company_name": "MOOO LIMITED",
         |  "company_type": "ltd",
         |    "type": "ltd",
         |    "company_status":"foo",
         |  "registered_office_address": {
         |    "country": "United Kingdom",
         |    "address_line_2": "foo2",
         |    "premises": "98",
         |    "po_box": "po1",
         |    "region":"region1",
         |    "care_of": "care of 1",
         |    "postal_code": "post1",
         |    "address_line_1": "lim1",
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
         |        "address_line_2": "add2",
         |        "premises": "98",
         |        "postal_code": "post1",
         |        "address_line_1": "lim1",
         |        "locality": "WORTHING"
         |      }
         |    },
         |    {
         |      "date_of_birth": {
         |        "month": "00",
         |        "day": "01",
         |        "year": "0002"
         |      },
         |      "name_elements": {
         |        "title": "Mx",
         |        "forename": "Jingly",
         |        "surname": "Jingles"
         |      },
         |      "address": {
         |        "country": "England",
         |        "premises": "111",
         |        "postal_code": "post2",
         |        "address_line_1": "add11",
         |        "locality": "local1"
         |      }
         |    }
         |  ],
         | "sic_codes": [
         |
           |  "84240",
         |   "01410"
         |
           |  ]
         |}
    """.stripMargin
    "return 200 if a company is incorporated and can be found in " in new Setup {

      val expected =
        s"""
                        {"company_type":"ltd",
                        "type":"ltd",
                        "registered_office_address":{
                        "country":"United Kingdom",
                        "address_line_2":"foo2",
                        "premises":"98",
                        "postal_code":"post1",
                        "address_line_1":"lim1",
                        "locality":"WORTHING"},
                        "company_name":"MOOO LIMITED",
                        "company_number":"crn1",
                        "sic_codes":[{"sic_code":"84240","sic_description":""},{"sic_code":"01410","sic_description":""}],
                        "company_status":"foo"}
    """.stripMargin
      val crn = "crn1"
      val cohoDestinationUrl = s"/cohoFrontEndStubs/company/$crn"
      stubGet(cohoDestinationUrl, 200, input)
      val incorpUpdate = IncorpUpdate(transactionId, "rejected", Some("crn1"), None, "tp", Some("description"))

      insert(incorpUpdate)
      val clientUrl = s"/incorporation-information/$transactionId/company-profile"
      println(clientUrl)
      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200
      val res = Json.parse(response.body).as[JsObject]
      res shouldBe Json.parse(expected).as[JsObject]
    }
  }
}
