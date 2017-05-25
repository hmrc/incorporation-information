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
import connectors.{FailedTransactionalAPIResponse, IncorporationAPIConnector, PublicCohoApiConnector, SuccessfulTransactionalAPIResponse}
import models.IncorpUpdate
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import play.api.libs.json._
import org.mockito.Mockito._
import repositories.IncorpUpdateRepository

import scala.concurrent.Future

class TransactionalServiceSpec extends SCRSSpec {

  val mockConnector = mock[IncorporationAPIConnector]
  val mockRepos = mock[IncorpUpdateRepository]
  val mockCohoConnector = mock[PublicCohoApiConnector]
  class Setup {
    val service = new TransactionalService {
      override protected val connector = mockConnector
      override val incorpRepo = mockRepos
      override val publicCohoConnector =  mockCohoConnector

    }
  }
  class SetupForCohoTransform {
    val service = new TransactionalService {
      override protected val connector = mockConnector
      override val incorpRepo = mockRepos
      override val publicCohoConnector =  mockCohoConnector
      val jsonObj = Json.obj("foo" -> Json.toJson("fooValue"))
      override def transformDataFromCoho(js: JsObject):Option[JsValue] = Future.successful(Some(jsonObj))
    }
  }

  def buildJson(txId: String = "000-033808") = Json.parse(
    s"""
      |{
      |  "transaction_id": "$txId",
        "company_name": "MOOO LIMITED",
      |  "type": "ltd",
      |  "registered_office_address": {
      |    "country": "United Kingdom",
      |    "address_line_2": "foo2",
      |    "premises": "prem1",
      |    "postal_code": "post1",
      |    "address_line_1": "address1",
      |    "locality": "locality1"
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
      |        "address_line_2": "address22",
      |        "premises": "Prem2",
      |        "postal_code": "post2",
      |        "address_line_1": "add11",
      |        "locality": "locality2"
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
      |        "premises": "Prem3",
      |        "postal_code": "post3",
      |        "address_line_1": "Add111",
      |        "locality": "locality3"
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

  val officerAppointmentJson = Json.parse(
    """
      |{
      |  "name": "test testy TESTERSON",
      |  "kind": "personal-appointment",
      |  "items": [
      |    {
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
      |      "officer_role": "director",
      |      "nationality": "British",
      |      "name": "test testy TESTERSON",
      |      "name_elements": {
      |        "other_forenames": "Testy",
      |        "title": "Mr",
      |        "surname": "TESTERSON",
      |        "forename": "Test"
      |      },
      |      "appointed_to": {
      |        "company_name": "TEST CONSULTANCY SERVICES LIMITED",
      |        "company_number": "SC999999",
      |        "company_status": "active"
      |      }
      |    }
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
    """.stripMargin)

  def publicOfficerListJson(officerAppointmentUrl: String = "/test/link") = Json.parse(
    s"""
      |{
      |  "active-count" : 1,
      |  "etag" : "f3f1374e8d4d3640fc1a117ac3cc4addfa11e19f",
      |  "inactive_count" : 0,
      |  "items" : [ {
      |    "address" : {
      |      "premises" : "14",
      |      "address_line_1" : "test avenue",
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
      |        "appointments" : "$officerAppointmentUrl"
      |      }
      |    },
      |    "name" : "TESTINGTON, Test Tester",
      |    "nationality" : "British",
      |    "occupation" : "Consultant",
      |    "officer_role" : "director"
      |  } ],
      |  "items_per_page" : 35,
      |  "kind" : "officer-list",
      |  "links" : {
      |    "self" : "/test/self-link"
      |  },
      |  "resigned_count" : 0,
      |  "start_index" : 0,
      |  "total_results" : 1
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

  "fetchCompanyProfile from transactional api" should {

    val transactionId = "12345"

    "return some Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(buildJson())
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))
      await(service.fetchCompanyProfileFromTx(transactionId)) shouldBe Some(companyProfileJson)
    }

    "return None from transactional api" when {

      "a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
        val response = FailedTransactionalAPIResponse
        when(mockConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchCompanyProfileFromTx(transactionId)) shouldBe None
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
   "checkIfCompIncorporated" should {
        "return Some(String) - the crn" when {
          "Company exists and has a crn" in new Setup {
            val incorpUpdate = IncorpUpdate("transId", "accepted", Some("foo"), None, "", None)
            when(mockRepos.getIncorpUpdate(Matchers.any[String])).thenReturn(Future.successful(Some(incorpUpdate)))
            await(service.checkIfCompIncorporated("fooBarTest")) shouldBe Some("foo")
          }
        }
               "return None" when {
                "Company exists, has a status != rejected but has no crn" in new Setup {
                     val incorpUpdate = IncorpUpdate("transId", "foo", None, None, "", None)
                     when(mockRepos.getIncorpUpdate(Matchers.any[String])).thenReturn(Future.successful(Some(incorpUpdate)))
                      await(service.checkIfCompIncorporated("fooBarTest")) shouldBe  None
                    }
               }
                "return None" when {
                    "Company does not exist" in new Setup {
                        when(mockRepos.getIncorpUpdate(Matchers.any[String])).thenReturn(Future.successful(None))
                        await(service.checkIfCompIncorporated("fooBarTest")) shouldBe  None
                      }
                }
        }
"fetchCompanyProfileFromCoho" should {
  "return None when companyProfile cannot be found" in new Setup {
    val response = FailedTransactionalAPIResponse
    when(mockCohoConnector.getCompanyProfile(Matchers.any[String])(any())).thenReturn(Future.successful(None))
    when(mockConnector.fetchTransactionalData(Matchers.any[String])(any())).thenReturn(Future.successful(response))
    await(service.fetchCompanyProfileFromCoho("num","")) shouldBe None
  }
  "return Some(json) when companyProfile can be found" in new SetupForCohoTransform {
    val json = buildJson("foo")
    when(mockCohoConnector.getCompanyProfile(Matchers.any[String])(any())).thenReturn(Future.successful(Some(service.jsonObj)))
    await(service.fetchCompanyProfileFromCoho("num","")) shouldBe Some(service.jsonObj)
  }
}

  "transformDataFromCoho" should {

      "return JSValue containing formatted data successfully" in new Setup {
        val input = Json.parse(
          s"""
             |{
             |  "transaction_id": "fef",
             |  "company_name": "MOOO LIMITED",
             |  "type": "ltd",
             |  "company_status": "foo",
             |  "company_number": "number",
             |  "registered_office_address": {
             |    "country": "United Kingdom",
             |    "address_line_2": "foo2",
             |    "premises": "prem1",
             |    "postal_code": "post1",
             |    "address_line_1": "address1",
             |    "locality": "locality1",
             |    "care_of": "caring1",
             |    "po_box": "poB",
             |    "region": "region1"
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
             |        "address_line_2": "address22",
             |        "premises": "Prem2",
             |        "postal_code": "post2",
             |        "address_line_1": "add11",
             |        "locality": "locality2"
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
             |        "premises": "Prem3",
             |        "postal_code": "post3",
             |        "address_line_1": "Add111",
             |        "locality": "locality3"
             |      }
             |    }
             |  ],
             |  "sic_codes": [
             |    "84240","01410"
             |  ]
             |             }
    """.stripMargin).as[JsObject]


        val expected = Json.parse(
          s"""
             |{
             |  "company_name": "MOOO LIMITED",
             |  "type": "ltd",
             |  "company_type": "ltd",
             |  "company_number": "number",
             |  "company_status": "foo",
             |
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

         await(service.transformDataFromCoho(input)) shouldBe Some(expected)
      }

    "return JSValue containing formatted data successfully without sic codes & company status as these are optional" in new Setup {
      val input = Json.parse(
        s"""
           |{
           |  "transaction_id": "fef",
           |  "company_name": "MOOO LIMITED",
           |  "type": "ltd",
           |  "company_number": "number",
           |  "registered_office_address": {
           |    "country": "United Kingdom",
           |    "address_line_2": "foo2",
           |    "premises": "prem1",
           |    "postal_code": "post1",
           |    "address_line_1": "address1",
           |    "locality": "locality1"
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
           |        "address_line_2": "address22",
           |        "premises": "Prem2",
           |        "postal_code": "post2",
           |        "address_line_1": "add11",
           |        "locality": "locality2"
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
           |        "premises": "Prem3",
           |        "postal_code": "post3",
           |        "address_line_1": "Add111",
           |        "locality": "locality3"
           |      }
           |    }
           |  ]
           |             }
    """.stripMargin).as[JsObject]


      val expected = Json.parse(
        s"""
           |{
           |  "company_name": "MOOO LIMITED",
           |  "type": "ltd",
           |  "company_type": "ltd",
           |  "company_number": "number",
           |  "registered_office_address": {
           |    "country": "United Kingdom",
           |    "address_line_2": "foo2",
           |    "premises": "prem1",
           |    "postal_code": "post1",
           |    "address_line_1": "address1",
           |    "locality": "locality1"
           |  }
           |
           |             }
    """.stripMargin).as[JsObject]

      await(service.transformDataFromCoho(input)) shouldBe Some(expected)
    }

  }
    "sicCodesConverter" should {
      "return Some(List[JsObjects]) of sicCodes when SicCodes are passed into the method" in new Setup {
        val input = Json.parse(
          s"""
             |{
             |"sic_codes": [
             |    "84240","01410"
             |  ]

           |             }
    """.stripMargin)

        val sicCodes = (input \ "sic_codes").toOption
        val output1 = Json.obj("sic_code" -> Json.toJson("84240"), "sic_description" -> Json.toJson(""))
        val output2 = Json.obj("sic_code" -> Json.toJson("01410"), "sic_description" -> Json.toJson(""))
        val listOfJsObjects = List(output1,output2)
        await(service.sicCodesConverter(sicCodes)) shouldBe Some(listOfJsObjects)


      }
    }

  "sicCodesConverter" should {
    "return None when None is passed in" in new Setup {
      await(service.sicCodesConverter(None)) shouldBe None
    }
  }

  "transformOfficerAppointment" should {

    val expected = Json.parse(
      """
        |{
        |  "other_forenames":"Testy",
        |  "title":"Mr",
        |  "surname":"TESTERSON",
        |  "forename":"Test"
        |}
      """.stripMargin)

    "transform and return the supplied json correctly" in new Setup {
      val result = await(service.transformOfficerAppointment(officerAppointmentJson))
      result shouldBe expected
    }

    "return a None if the key 'name_elements' cannot be found in the supplied Json" in new Setup {
      val incorrectJson = Json.parse("""{"test":"json"}""")
      intercept[NoItemsFoundException](await(service.transformOfficerAppointment(incorrectJson)))
    }
  }

  "fetchOfficerAppointment" should {

    val url = "test/url"

    "return a transformed officer appointment Json" in new Setup {
      when(mockCohoConnector.getOfficerAppointment(eqTo(url))(any()))
        .thenReturn(Future.successful(officerAppointmentJson))

      service.fetchOfficerAppointment(url)
    }

    "return None when an officer appointment cannot be found in the public API" in new Setup {
      when(mockCohoConnector.getOfficerAppointment(eqTo(url))(any()))
        .thenReturn(Future.successful(Json.obj()))

      service.fetchOfficerAppointment(url)
    }

    "return None when the returned officer appointment cannot be transformed" in new Setup {
      val incorrectJson = Json.parse("""{"test":"json"}""")
      when(mockCohoConnector.getOfficerAppointment(eqTo(url))(any()))
        .thenReturn(Future.successful(incorrectJson))

      service.fetchOfficerAppointment(url)
    }
  }

  "transformOfficerList" should {

    val publicOfficerJson = Json.parse(
      """
        |{
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
        |    "officer_role" : "director"
        | }
      """.stripMargin)

    "transform the supplied json into the pre-incorp officer list json structure" in new Setup {

      val expected = Json.parse(
        """
          |{
          |  "date_of_birth": {
          |    "month": 3,
          |    "year": 1990
          |  },
          |  "address": {
          |    "address_line_1": "test avenue",
          |    "country": "United Kingdom",
          |    "address_line_2": "test line 2",
          |    "premises": "14",
          |    "postal_code": "TE1 1ST",
          |    "locality" : "testville"
          |  }
          |}
        """.stripMargin)

      val result = service.transformOfficerList(publicOfficerJson)
      result shouldBe expected
    }
  }

  "fetchOfficerListFromPublicAPI" should {

    val crn = "crn-0123456789"
    val url = "test/url"

    "return a fully formed officer list json structure" in new Setup {

      val expected = Json.parse(
        """
          |{
          |  "officers" : [
          |    {
          |      "date_of_birth" : {
          |        "month" : 3,
          |        "year" : 1990
          |      },
          |      "address" : {
          |        "address_line_1" : "test avenue",
          |        "country" : "United Kingdom",
          |        "locality" : "testville",
          |        "premises" : "14",
          |        "postal_code" : "TE1 1ST"
          |      },
          |      "name_elements" : {
          |        "other_forenames" : "Testy",
          |        "title" : "Mr",
          |        "surname" : "TESTERSON",
          |        "forename" : "Test"
          |      }
          |    }
          |  ]
          |}
        """.stripMargin)

      when(mockCohoConnector.getOfficerList(any())(any()))
        .thenReturn(Future.successful(Some(publicOfficerListJson(url))))
      when(mockCohoConnector.getOfficerAppointment(any())(any()))
        .thenReturn(Future.successful(officerAppointmentJson))

      val result = await(service.fetchOfficerListFromPublicAPI(crn))
      result shouldBe Seq(expected)
    }
  }
}

