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

package connectors

import helpers.IntegrationSpecBase
import models.IncorpUpdate
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import repositories.IncorpUpdateMongo
import utils.TimestampFormats._

import scala.concurrent.ExecutionContext.Implicits.global
class TransactionalAndPublicAPIISpec extends IntegrationSpecBase {

  val publicCohoStubUri = "/cohoFrontEndStubs"

  val additionalConfiguration = Map(
    "metrics.enabled" -> true,
    "microservice.services.incorp-update-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/incorporation-frontend-stubs",
    "microservice.services.incorp-update-api.url" -> s"http://${wiremockHost}:${wiremockPort}",
    "microservice.services.public-coho-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}$publicCohoStubUri"
  )

  //todo: set feature switch to false
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .build()

  class Setup {
    val incRepo = app.injector.instanceOf[IncorpUpdateMongo].repo
    await(incRepo.drop)
    await(incRepo.ensureIndexes)
    def insert(update: IncorpUpdate) = await(incRepo.insert(update))
  }

  val incorpDate = DateTime.parse("2018-05-01", DateTimeFormat.forPattern(datePattern))

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

    "return 200 if a company is incorporated and can be found in Public API" in new Setup {
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
      val incorpUpdate = IncorpUpdate(transactionId, "foo", Some(crn), Some(incorpDate), "tp", Some("description"))
      insert(incorpUpdate)

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val cohoDestinationUrl = s"$publicCohoStubUri/company-profile/$crn"

      stubGet(cohoDestinationUrl, 200, input)

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200

      val res = Json.parse(response.body).as[JsObject]

      res shouldBe Json.parse(expected).as[JsObject]
    }

    "return 404 if a company is incorporated but cannot be found in Public API or Transactional API" in new Setup {

      val crn = "crn2"
      val clientUrl = s"/incorporation-information/$transactionId/company-profile"
      val cohoDestinationUrl = s"/cohoFrontEndStubs/company-profile/$crn"
      stubGet(cohoDestinationUrl, 404, "")
      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 404
    }

    "return information from transactional API if a company is incorporated but cannot be found in Public API" in new Setup {

      val transactionAPIUrl = s"/incorporation-frontend-stubs/fetch-data/$transactionId"
      val transactionalAPIBody =
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

      val expectedTXBody =
        s"""
          {
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
           |   "sic_codes": [
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

        stubGet(transactionAPIUrl, 200, transactionalAPIBody)


        val crn = "crn2"
        val clientUrl = s"/incorporation-information/$transactionId/company-profile"
        val cohoDestinationUrl = s"/cohoFrontEndStubs/company-profile/$crn"
        stubGet(cohoDestinationUrl, 404, "")
        val response = buildClient(clientUrl).get().futureValue
        response.status shouldBe 200

      val res = Json.parse(response.body).as[JsObject]

      res shouldBe Json.parse(expectedTXBody).as[JsObject]
      }

    }
  "getOfficerList" should {
    val crn = "crn5"
    val input =
      s"""
         |{
         |  "transaction_id": "12345",
         |  "company_number": "crn5",
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
         |    """.stripMargin
    val dateTime = Json.toJson(DateTime.parse("2017-05-15T17:45:45Z"))
    val officerListInput =
      s"""
                                |{ "items":[{
                                |   "address" : {
                                |      "premises" : "14",
                                |      "address_line_1" : "test avenue",
                                |      "address_line_2" : "test line 2",
                                |      "locality" : "testville",
                                |      "country" : "United Kingdom",
                                |      "postal_code" : "TE1 1ST",
                                |      "region" : "testshire"
                                |    },
                                |    "appointed_on" : 1494866745000,
                                |    "country_of_residence" : "England",
                                |    "date_of_birth" : {
                                |      "month" : 3,
                                |      "year" : 1990
                                |    },
                                |    "former_names" : [ ],
                                |    "links" : {
                                |      "officer" : {
                                |        "appointments" : "/test/link"
                                |      }
                                |    },
                                |    "name" : "TESTINGTON, Test Tester",
                                |    "nationality" : "British",
                                |    "occupation" : "Consultant",
                                |    "officer_role" : "director",
                                |    "resigned_on" : "$dateTime"
                                |
                                | }
                                | ]}
      """.stripMargin

    val naturalName =
      s"""
         |      "name": "test testy TESTERSON",
         |      "name_elements": {
         |        "other_forenames": "Testy",
         |        "title": "Mr",
         |        "surname": "TESTERSON",
         |        "forename": "Test"
         |      }
       """.stripMargin

    val corporateName =
      s"""
         |      "name": "test testy TESTERSON"
       """.stripMargin

    def appointment1(nameJson: String, role: String) =
      s"""|{
          |      "links": {
          |        "company": "/company/SC999999"
          |      },
          |      "occupation": "Consultant",
          |      "country_of_residence": "Scotland",
          |      "address": {
          |        "premises": "14",
          |        "address_line_1": "Test Avenue",
          |        "region": "Testshire",
          |        "postal_code": "TE0 1ST",
          |        "locality": "testkirk",
          |        "country": "United Kingdom"
          |      },
          |      "appointed_on": "2017-02-10",
          |      "officer_role": "${role}",
          |      "nationality": "British",
          |      ${nameJson},
          |      "appointed_to": {
          |        "company_name": "TEST CONSULTANCY SERVICES LIMITED",
          |        "company_number": "SC999999",
          |        "company_status": "active"
          |      }
          |}
    """.stripMargin

    def appointments(appointmentJson: String) =
      s"""|{
        |  "name": "test testy TESTERSON",
        |  "kind": "personal-appointment",
        |  "items": [
        |  ${appointmentJson}
        |  ],
        |  "date_of_birth": {
          |    "month": 3,
          |    "year": 1964
          |  },
        |  "is_corporate_officer": false,
        |  "start_index": 0,
        |  "total_results": 1,
        |  "items_per_page": 35,
        |  "links": {
          |    "self": "/officers/zzzV2JvcOvjFzi5f6Te05SbuWS1/appointments"
          |  },
        |  "etag": "1db68f787d91310e659ab22690d504363aeb9361"
        |}
    """.stripMargin
    val cohoDestinationUrl = s"/cohoFrontEndStubs/company-profile/$crn"
    val  cohOfficerListUrl = s"/cohoFrontEndStubs/company/$crn/officers"

    "return 404 if no officer list exists but company is incorporated  no data in tx api /public api" in new Setup {
      val transactionId = "12345"

      await(incRepo.drop)
      // insert into incorp info db -> company is registered so dont go to tx api
      val incorpUpdate = IncorpUpdate(transactionId, "foo", Some("crn5"), Some(incorpDate), "tp", Some("description"))
      insert(incorpUpdate)
      //fail to get officer list can be 404 / 500 etc, as long as not 200 this test is valid
      stubGet(cohOfficerListUrl, 404, "")
      //nothing is in tx api. so overall we will get 404
      val clientUrl = s"/incorporation-information/$transactionId/officer-list"
      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 404
    }

    "return 200 if company incorporated, officer list exists in public api with natural director" in new Setup {
      val transactionId = "12345"
      await(incRepo.drop)
      // insert into incorp info db -> company is registered so dont go to tx api
      val incorpUpdate = IncorpUpdate(transactionId, "foo", Some("crn5"), Some(incorpDate), "tp", Some("description"))
      insert(incorpUpdate)
      //succeed in getting officer list
      stubGet(cohOfficerListUrl, 200, officerListInput)
      stubGet("/cohoFrontEndStubs/get-officer-appointment?.*", 200,appointments(appointment1(naturalName, "director")))
      val clientUrl = s"/incorporation-information/$transactionId/officer-list"
      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200
      // TODO - Add assertions for response
    }

    "return 200 if company incorporated, officer list exists in public api with corporate director" in new Setup {
      val transactionId = "12345"
      await(incRepo.drop)
      // insert into incorp info db -> company is registered so dont go to tx api
      val incorpUpdate = IncorpUpdate(transactionId, "foo", Some("crn5"), Some(incorpDate), "tp", Some("description"))
      insert(incorpUpdate)
      //succeed in getting officer list
      stubGet(cohOfficerListUrl, 200, officerListInput)
      stubGet("/cohoFrontEndStubs/get-officer-appointment?.*", 200,appointments(appointment1(corporateName, "corporate-director")))
      val clientUrl = s"/incorporation-information/$transactionId/officer-list"
      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200
      // TODO - Add assertions for response
    }
  }
}
