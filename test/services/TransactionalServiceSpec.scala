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

package services

import Helpers.{LogCapturing, SCRSSpec}
import connectors.{FailedTransactionalAPIResponse, IncorporationAPIConnector, PublicCohoApiConnectorImpl, SuccessfulTransactionalAPIResponse}
import models.IncorpUpdate
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.mockito.Matchers
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.Logger
import play.api.libs.json.JodaWrites.JodaDateTimeNumberWrites
import play.api.libs.json._
import repositories.IncorpUpdateRepository
import utils.TimestampFormats
import play.api.test.Helpers._

import scala.concurrent.Future

class TransactionalServiceSpec extends SCRSSpec with LogCapturing {

  val mockConnector = mock[IncorporationAPIConnector]
  val mockRepos = mock[IncorpUpdateRepository]
  val mockCohoConnector = mock[PublicCohoApiConnectorImpl]

  class Setup {
    val service = new TransactionalService {
      override protected val connector = mockConnector
      override val incorpRepo = mockRepos
      override val publicCohoConnector = mockCohoConnector

    }
  }

  class SetupForCohoTransform {
    val service = new TransactionalService {
      override protected val connector = mockConnector
      override val incorpRepo = mockRepos
      override val publicCohoConnector = mockCohoConnector
      val jsonObj = Json.obj("foo" -> Json.toJson("fooValue"))

      override def transformDataFromCoho(js: JsObject): Option[JsValue] = Some(jsonObj)
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
       |    "officer_role" : "director",
       |    "resigned_on" : "$dateTime"
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

  val officersJson = Json.obj("officers" -> (buildJson() \ "officers").get.as[JsArray])
  val companyProfileJson = (buildJson().as[JsObject] - "officers").as[JsValue]

  val incorrectJson = Json.parse(
    """
      |{
      | "incorrect":"json"
      |}
    """.stripMargin)

  val transactionId = "tx-12345"
  val crn = "XX010101"

  val dateTime = Json.toJson(DateTime.parse("2017-05-15T17:45:45Z"))(JodaDateTimeNumberWrites)

  "extractJson" should {

    "fetch a json sub-document from a successful TransactionalAPIResponse by the supplied key" in new Setup {
      val json = buildJson()
      val transformer = (JsPath \ "officers").json.prune

      val response = SuccessfulTransactionalAPIResponse(json)

      await(service.extractJson(Future.successful(response), transformer)) shouldBe Some(companyProfileJson)
    }

    "return a None when attempting to fetch a sub-document from a successful TransactionalAPIResponse with an un-matched transformer key" in new Setup {
      val json = buildJson()
      val transformer = (JsPath \ "unmatched").json.prune

      val response = SuccessfulTransactionalAPIResponse(json)

      await(service.extractJson(Future.successful(response), transformer)) shouldBe None
    }

    "return a None when a FailedTransactionalAPIResponse is returned" in new Setup {
      val response = FailedTransactionalAPIResponse
      val transformer = (JsPath \ "officers").json.prune

      await(service.extractJson(Future.successful(response), transformer)) shouldBe None
    }
  }

  "fetchCompanyProfile from transactional api" should {

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
    }
  }

  "fetchOfficerList" should {

    val crn = "crn-12345"

    val incorpUpdate = IncorpUpdate(transactionId, "accepted", Some(crn), Some(DateTime.parse("2018-05-01", DateTimeFormat.forPattern(TimestampFormats.datePattern))), "", None)

    "return Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(buildJson())
      when(mockRepos.getIncorpUpdate(any())).thenReturn(Future.successful(None))
      when(mockConnector.fetchTransactionalData(any())(any()))
        .thenReturn(Future.successful(response))

      await(service.fetchOfficerList(transactionId)) shouldBe officersJson
    }

    "return FailedToFetchOfficerListFromTxAPI when a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
      val response = FailedTransactionalAPIResponse
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))

      intercept[FailedToFetchOfficerListFromTxAPI](await(service.fetchOfficerList(transactionId)))
    }

    "return a JsResultException when a SuccessfulTransactionalAPIResponse is returned for the supplied transaction Id but an incorrect json document is provided" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(incorrectJson)
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))

      intercept[JsResultException](await(service.fetchOfficerList(transactionId)))
    }

    "return a transformed officer list when an incorporated company is fetched from Incorp Info Repo" in new Setup {
      when(mockRepos.getIncorpUpdate(any())).thenReturn(Future.successful(Some(incorpUpdate)))
      when(mockCohoConnector.getOfficerList(any())(any()))
        .thenReturn(Future.successful(Some(publicOfficerListJson())))
      when(mockCohoConnector.getOfficerAppointment(any())(any()))
        .thenReturn(Future.successful(officerAppointmentJson))

      val expected = Json.parse(
        s"""
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
           |      },
           |        "officer_role" : "director",
           |        "resigned_on" : "$dateTime",
           |        "appointment_link":"/test/link"
           |    }
           |  ]
           |}
        """.stripMargin)

      await(service.fetchOfficerList(transactionId)) shouldBe expected
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

        await(service.checkIfCompIncorporated("fooBarTest")) shouldBe None
      }

      "Company does not exist" in new Setup {
        when(mockRepos.getIncorpUpdate(Matchers.any[String])).thenReturn(Future.successful(None))
        await(service.checkIfCompIncorporated("fooBarTest")) shouldBe None
      }
    }
  }

  "fetchCompanyProfileFromCoho" should {

    "return None when companyProfile cannot be found" in new Setup {
      val response = FailedTransactionalAPIResponse
      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any())).thenReturn(Future.successful(None))
      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any())).thenReturn(Future.successful(response))
      await(service.fetchCompanyProfileFromCoho("num", "")) shouldBe None
    }

    "return Some(json) when companyProfile can be found" in new SetupForCohoTransform {
      val json = buildJson("foo")
      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any())).thenReturn(Future.successful(Some(service.jsonObj)))
      await(service.fetchCompanyProfileFromCoho("num", "")) shouldBe Some(service.jsonObj)
    }
  }

  "transformDataFromCoho" should {

    "return JSValue containing formatted data successfully" in new Setup {
      val input = Json.parse(
        s"""
             {
           |   "accounts" : {
           |      "accounting_reference_date" : {
           |         "day" : "integer",
           |         "month" : "integer"
           |      },
           |      "last_accounts" : {
           |         "made_up_to" : "date",
           |         "period_end_on" : "date",
           |         "period_start_on" : "date",
           |         "type" : "string"
           |      },
           |      "next_accounts" : {
           |         "due_on" : "date",
           |         "overdue" : "boolean",
           |         "period_end_on" : "date",
           |         "period_start_on" : "date"
           |      },
           |      "next_due" : "date",
           |      "next_made_up_to" : "date",
           |      "overdue" : "boolean"
           |   },
           |   "annual_return" : {
           |      "last_made_up_to" : "date",
           |      "next_due" : "date",
           |      "next_made_up_to" : "date",
           |      "overdue" : "boolean"
           |   },
           |   "branch_company_details" : {
           |      "business_activity" : "string",
           |      "parent_company_name" : "string",
           |      "parent_company_number" : "string"
           |   },
           |   "can_file" : "boolean",
           |   "company_name" : "string",
           |   "company_number" : "string",
           |   "company_status" : "string",
           |   "company_status_detail" : "string",
           |   "confirmation_statement" : {
           |      "last_made_up_to" : "date",
           |      "next_due" : "date",
           |      "next_made_up_to" : "date",
           |      "overdue" : "boolean"
           |   },
           |   "date_of_cessation" : "date",
           |   "date_of_creation" : "date",
           |   "etag" : "string",
           |   "foreign_company_details" : {
           |      "accounting_requirement" : {
           |         "foreign_account_type" : "string",
           |         "terms_of_account_publication" : "string"
           |      },
           |      "accounts" : {
           |         "account_period_from" : {
           |            "day" : "integer",
           |            "month" : "integer"
           |         },
           |         "account_period_to" : {
           |            "day" : "integer",
           |            "month" : "integer"
           |         },
           |         "must_file_within" : {
           |            "months" : "integer"
           |         }
           |      },
           |      "business_activity" : "string",
           |      "company_type" : "string",
           |      "governed_by" : "string",
           |      "is_a_credit_finance_institution" : "boolean",
           |      "originating_registry" : {
           |         "country" : "string",
           |         "name" : "string"
           |      },
           |      "registration_number" : "string"
           |   },
           |   "has_been_liquidated" : "boolean",
           |   "has_charges" : "boolean",
           |   "has_insolvency_history" : "boolean",
           |   "is_community_interest_company" : "boolean",
           |   "jurisdiction" : "string",
           |   "last_full_members_list_date" : "date",
           |   "links" : {
           |      "charges" : "string",
           |      "filing_history" : "string",
           |      "insolvency" : "string",
           |      "officers" : "string",
           |      "persons_with_significant_control" : "string",
           |      "persons_with_significant_control_statements" : "string",
           |      "registers" : "string",
           |      "self" : "string"
           |   },
           |   "partial_data_available" : "string",
           |   "previous_company_names" : [
           |      {
           |         "ceased_on" : "date",
           |         "effective_from" : "date",
           |         "name" : "string"
           |      }
           |   ],
           |   "registered_office_is_in_dispute" : "boolean",
           |   "sic_codes" : [
           |      "string"
           |   ],
           |   "type" : "string",
           |   "undeliverable_registered_office_address" : "boolean"
           |}
    """.stripMargin).as[JsObject]


      val expected = Json.parse(
        s"""
           |{
           |  "company_name": "string",
           |  "type": "string",
           |  "company_type": "string",
           |  "company_number": "string",
           |  "company_status": "string",
           |
             |
             |   "sic_codes": [
           |    {
           |      "sic_description": "",
           |      "sic_code": "string"
           |    }
           |
             |  ]
           |             }
    """.stripMargin).as[JsObject]

      service.transformDataFromCoho(input) shouldBe Some(expected)
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

      service.transformDataFromCoho(input) shouldBe Some(expected)
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
      val listOfJsObjects = List(output1, output2)
      service.sicCodesConverter(sicCodes) shouldBe Some(listOfJsObjects)


    }
  }

  "sicCodesConverter" should {
    "return None when None is passed in" in new Setup {
      service.sicCodesConverter(None) shouldBe None
    }
  }

  "transformOfficerAppointment" should {

    val expected = Json.parse(
      """
        |{
        |  "name_elements" : {
        |    "other_forenames":"Testy",
        |    "title":"Mr",
        |    "surname":"TESTERSON",
        |    "forename":"Test"
        |  }
        |}
      """.stripMargin)

    "transform and return the supplied json correctly" in new Setup {
      val result = service.transformOfficerAppointment(officerAppointmentJson)
      result shouldBe Some(expected)
    }

    "return a None if the key 'name_elements' cannot be found in the supplied Json" in new Setup {
      val incorrectJson = Json.parse("""{"test":"json"}""")
      intercept[NoItemsFoundException](service.transformOfficerAppointment(incorrectJson))
    }
    "correctly return None if items can be found but Name elements cannot be found" in new Setup {
      val res = service.transformOfficerAppointment(Json.parse("""{"items":[{"foo":"bar"}]}"""))
      res shouldBe None
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
      s"""
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
         |    "officer_role" : "director",
         |    "resigned_on" : "$dateTime"
         | }
      """.stripMargin)

    "transform the supplied json into the pre-incorp officer list json structure" in new Setup {

      val expected = Json.parse(
        s"""
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
           |  },
           |  "officer_role": "director",
           |  "resigned_on" : "$dateTime",
           |  "appointment_link": "/test/link"
           |}
        """.stripMargin)

      val result = service.transformOfficerList(publicOfficerJson)
      result shouldBe expected
    }

    "transform the supplied json into the pre-incorp officer list json structure when an officers address does not contain a country" in new Setup {

      val publicOfficerJsonWithoutCountry = Json.parse(
        s"""
           |{
           |   "address" : {
           |      "premises" : "14",
           |      "address_line_1" : "test avenue",
           |      "address_line_2" : "test line 2",
           |      "locality" : "testville",
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
           | }
      """.stripMargin)

      val expected = Json.parse(
        s"""
           |{
           |  "date_of_birth": {
           |    "month": 3,
           |    "year": 1990
           |  },
           |  "address": {
           |    "address_line_1": "test avenue",
           |    "address_line_2": "test line 2",
           |    "premises": "14",
           |    "postal_code": "TE1 1ST",
           |    "locality" : "testville"
           |  },
           |  "officer_role": "director",
           |  "resigned_on" : "$dateTime",
           |  "appointment_link": "/test/link"
           |}
        """.stripMargin)

      val result = service.transformOfficerList(publicOfficerJsonWithoutCountry)
      result shouldBe expected
    }
  }

  "fetchOfficerListFromPublicAPI" should {

    val crn = "crn-0123456789"
    val url = "/test/link"

    "return a fully formed officer list json structure when 1 officer is retrieved" in new Setup {

      val expected = Json.parse(
        s"""
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
           |      },
           |       "officer_role" : "director",
           |       "resigned_on" : "$dateTime",
           |       "appointment_link":"/test/link"
           |    }
           |
          |  ]
           |}
        """.stripMargin)

      when(mockCohoConnector.getOfficerList(any())(any()))
        .thenReturn(Future.successful(Some(publicOfficerListJson(url))))
      when(mockCohoConnector.getOfficerAppointment(any())(any()))
        .thenReturn(Future.successful(officerAppointmentJson))

      val result = await(service.fetchOfficerListFromPublicAPI(transactionId, crn))
      result shouldBe expected
    }

    "return an officer list without name elements when none are provided by the public API" in new Setup {

      val expected = Json.parse(
        s"""
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
           |      "officer_role" : "director",
           |      "resigned_on" : "$dateTime",
           |      "appointment_link":"/test/link"
           |    }
           |  ]
           |}
        """.stripMargin)

      val officerAppointmentJsonWithoutNameElements = {
        val items = Json.obj("items" -> JsArray((officerAppointmentJson \ "items").as[Seq[JsObject]].map(_ - "name_elements")))
        officerAppointmentJson.as[JsObject] deepMerge items
      }

      when(mockCohoConnector.getOfficerList(any())(any()))
        .thenReturn(Future.successful(Some(publicOfficerListJson(url))))
      when(mockCohoConnector.getOfficerAppointment(any())(any()))
        .thenReturn(Future.successful(officerAppointmentJsonWithoutNameElements))

      val result = await(service.fetchOfficerListFromPublicAPI(transactionId, crn))
      result shouldBe expected
    }

    "return a fully formed officer list json structure when 2 officers are retrieved" in new Setup {

      val listWith2Officers = Json.parse(
        s"""
           |{
           |  "active-count" : 1,
           |  "etag" : "f3f1374e8d4d3640fc1a117ac3cc4addfa11e19f",
           |  "inactive_count" : 0,
           |  "items" : [
           |    {
           |      "address" : {
           |        "premises" : "14",
           |        "address_line_1" : "test avenue",
           |        "locality" : "testville",
           |        "country" : "United Kingdom",
           |        "postal_code" : "TE1 1ST",
           |        "region" : "testshire"
           |      },
           |      "appointed_on" : 1494866745000,
           |      "country_of_residence" : "England",
           |      "date_of_birth" : {
           |        "month" : 3,
           |        "year" : 1990
           |      },
           |      "former_names" : [ ],
           |      "links" : {
           |        "officer" : {
           |          "appointments" : "/test/link"
           |        }
           |      },
           |      "name" : "TESTINGTON, Test Tester",
           |      "nationality" : "British",
           |      "occupation" : "Consultant",
           |      "officer_role" : "director",
           |      "resigned_on": "$dateTime"
           |    },
           |    {
           |      "address" : {
           |        "premises" : "14",
           |        "address_line_1" : "test avenue",
           |        "locality" : "testville",
           |        "country" : "United Kingdom",
           |        "postal_code" : "TE1 1ST",
           |        "region" : "testshire"
           |      },
           |      "appointed_on" : 1494866745000,
           |      "country_of_residence" : "England",
           |      "date_of_birth" : {
           |        "month" : 3,
           |        "year" : 1990
           |      },
           |      "former_names" : [ ],
           |      "links" : {
           |        "officer" : {
           |          "appointments" : "/test/link"
           |        }
           |      },
           |      "name" : "TESTINGTON, Test Tester",
           |      "nationality" : "British",
           |      "occupation" : "Consultant",
           |      "officer_role" : "director"
           |    }
           |  ],
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

      val expected = Json.parse(
        s"""
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
           |      },
           |      "officer_role" : "director",
           |      "resigned_on" : "$dateTime",
           |      "appointment_link": "/test/link"
           |    },
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
           |      },
           |        "officer_role" : "director",
           |        "appointment_link": "/test/link"
           |    }
           |  ]
           |}
        """.stripMargin)

      when(mockCohoConnector.getOfficerList(any())(any()))
        .thenReturn(Future.successful(Some(listWith2Officers)))
      when(mockCohoConnector.getOfficerAppointment(any())(any()))
        .thenReturn(Future.successful(officerAppointmentJson))

      val result = await(service.fetchOfficerListFromPublicAPI(transactionId, crn))
      result shouldBe expected
    }
  }

  "fetchSicCodes" should {

    "return None if no json was returned by the public API" in new Setup {
      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      await(service.fetchSicCodes(crn, usePublicAPI = true)) shouldBe None
    }

    "return None if no json was returned by the transactional API" in new Setup {
      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      await(service.fetchSicCodes(crn, usePublicAPI = false)) shouldBe None
    }

    "return None if no SIC codes were returned by the public API" in new Setup {
      val unexpectedResponse = Json.parse("""{ "foo":"bar" }""")

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(unexpectedResponse)))

      await(service.fetchSicCodes(crn, usePublicAPI = true)) shouldBe None
    }

    "return None if no SIC codes were returned by the transactional API" in new Setup {
      val unexpectedResponse = Json.parse("""{ "foo":"bar" }""")

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(unexpectedResponse)))

      await(service.fetchSicCodes(crn, usePublicAPI = false)) shouldBe None
    }

    "return a JsArray of SIC codes returned by the public API" in new Setup {
      val expectedJson = Json.parse(
        s"""{
           |  "company_name": "company's name",
           |  "type": "limited",
           |  "company_type": "limited",
           |  "company_number": "$crn",
           |  "company_status": "status",
           |  "sic_codes":[
           |    "12345",
           |    "67890"
           |  ]
           |}
        """.stripMargin).as[JsObject]
      val expectedCodes: JsValue = Json.parse("""{"sic_codes": ["12345","67890"]}""")

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(service.fetchSicCodes(crn, usePublicAPI = true)) shouldBe Some(expectedCodes)
    }

    "return a JsArray of SIC codes returned by the transactional API" in new Setup {
      val expectedJson = Json.parse(
        s"""{
           |  "sic_codes": [
           |    {
           |      "sic_description": "Some code description",
           |      "sic_code": "12345"
           |    },
           |    {
           |      "sic_description": "Standard code about Industry",
           |      "sic_code": "67890"
           |    }
           |  ]
           |}
        """.stripMargin).as[JsObject]
      val expectedCodes: JsValue = Json.parse("""{"sic_codes": ["12345","67890"]}""")

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(expectedJson)))

      await(service.fetchSicCodes(transactionId, usePublicAPI = false)) shouldBe Some(expectedCodes)
    }

    "return None when the JSON is formatted incorrectly" in new Setup {
      val expectedJson = Json.parse(
        s"""{
           |  "sic_codes":
           |    {
           |      "sic_description": "Some code description",
           |      "sic_code": "12345"
           |    }
           |}
        """.stripMargin).as[JsObject]
      val expectedCodes: JsValue = Json.parse("""{"sic_codes": ["12345","67890"]}""")

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(service.fetchSicCodes(crn, usePublicAPI = true)) shouldBe None
    }
  }

  "fetchSicCodesByCRN" should {
    val expectedJson = Json.parse(
      s"""{
         |  "company_name": "company's name",
         |  "type": "limited",
         |  "company_type": "limited",
         |  "company_number": "$crn",
         |  "company_status": "status",
         |  "sic_codes":[
         |    "12345",
         |    "67890"
         |  ]
         |}
        """.stripMargin).as[JsObject]
    val expectedCodes: JsValue = Json.parse("""{"sic_codes": ["12345","67890"]}""")

    "return some SIC codes" in new Setup {
      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(service.fetchSicByCRN(crn)) shouldBe Some(expectedCodes)
    }
    "return no SIC codes" in new Setup {
      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      await(service.fetchSicByCRN(crn)) shouldBe None
    }
  }

  "fetchSicCodesByCRN" should {
    val expectedJson = Json.parse(
      s"""{
         |  "company_name": "company's name",
         |  "type": "limited",
         |  "company_type": "limited",
         |  "company_number": "$crn",
         |  "company_status": "status",
         |  "sic_codes":[
         |    "12345",
         |    "67890"
         |  ]
         |}
        """.stripMargin).as[JsObject]

    val transactionalJson = Json.parse(
      s"""{
         |  "sic_codes": [
         |    {
         |      "sic_description": "Some code description",
         |      "sic_code": "12345"
         |    },
         |    {
         |      "sic_description": "Standard code about Industry",
         |      "sic_code": "67890"
         |    }
         |  ]
         |}
        """.stripMargin).as[JsObject]
    val expectedCodes: JsValue = Json.parse("""{"sic_codes": ["12345","67890"]}""")

    def incorporated(boolean: Boolean) = Future.successful(Option(IncorpUpdate(transactionId, "", if (boolean) Some(crn) else None, None, "")))

    "return some SIC codes from the public API if an incorporation update exists for the given transaction ID" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(true))

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(service.fetchSicByTransId(transactionId)) shouldBe Some(expectedCodes)
    }

    "return some SIC codes from the internal API if an incorporation update exists but the Public API returned nothing" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(true))

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(transactionalJson)))

      await(service.fetchSicByTransId(transactionId)) shouldBe Some(expectedCodes)
    }

    "return no SIC codes from the either API if an incorporation update exists but both APIs returned nothing" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(true))

      when(mockCohoConnector.getCompanyProfile(Matchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      await(service.fetchSicByTransId(transactionId)) shouldBe None
    }

    "return no SIC codes from the internal API if an incorporation update doesn't exist" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(false))

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      await(service.fetchSicByTransId(transactionId)) shouldBe None
    }

    "return SIC codes from the internal API if an incorporation update doesn't exist" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(false))

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      await(service.fetchSicByTransId(transactionId)) shouldBe None
    }
  }
  "fetchShareholders" should {
    val txJsonContainingShareholders = Json.parse(
      s"""
         |{
         |  "transaction_id": "$transactionId",
         |  "company_name": "MOOO LIMITED",
         |  "type": "ltd",
         |  "shareholders": [
         |  {
         |  "subscriber_type": "corporate",
         |    "name": "big company",
         |    "address": {
         |    "premises": "11",
         |    "address_line_1": "Drury Lane",
         |    "address_line_2": "West End",
         |    "locality": "London",
         |    "country": "United Kingdom",
         |    "postal_code": "SW2 B2B"
         |    },
         |  "percentage_voting_rights": 75.34
         |  }
         |  ]
         |}""".stripMargin)

    "return JsArray containing the list of shareholders logging the amount of shareholders in the array" in new Setup {
      val extractedJson = Json.parse(
        """[
          |  {
          |  "subscriber_type": "corporate",
          |  "name": "big company",
          |  "address": {
          |    "premises": "11",
          |    "address_line_1": "Drury Lane",
          |    "address_line_2": "West End",
          |    "locality": "London",
          |    "country": "United Kingdom",
          |    "postal_code": "SW2 B2B"
          |    },
          |  "percentage_voting_rights": 75.34
          |   }
          | ]
          |  """.stripMargin).as[JsArray]

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(txJsonContainingShareholders)))
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        await(service.fetchShareholders(transactionId)) shouldBe Some(extractedJson)
        val message = "[fetchShareholders] returned an array with the size - 1"
        val log =  logEvents.map(l => (l.getLevel, l.getMessage)).head
        log._1.toString shouldBe "INFO"
        log._2 shouldBe message
      }
    }
    "return JsArray containing empty list of shareholders, but key exists logging size at level WARN" in new Setup {
      val txJsonContainingEmptyListOfShareholders =
        Json.parse(
          s"""
             |{
             |  "transaction_id": "$transactionId",
             |  "company_name": "MOOO LIMITED",
             |  "type": "ltd",
             |  "shareholders" : []
             |
             |  }""".stripMargin)
      val extractedJson =
        Json.parse(
          """[]
          """.stripMargin)
      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(txJsonContainingEmptyListOfShareholders)))
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        await(service.fetchShareholders(transactionId)) shouldBe Some(extractedJson)
        val message = "[fetchShareholders] returned an array with the size - 0"
        val log = logEvents.map(l => (l.getLevel, l.getMessage)).head
        log._1.toString shouldBe "WARN"
        log._2 shouldBe message
      }
    }
    "return None when key does not exist" in new Setup {
      val txJsonContainingNOShareholdersKey =
        Json.parse(
          s"""
             |{
             |  "transaction_id": "$transactionId",
             |  "company_name": "MOOO LIMITED",
             |  "type": "ltd"
             |  }""".stripMargin)

      when(mockConnector.fetchTransactionalData(Matchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(txJsonContainingNOShareholdersKey)))
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        await(service.fetchShareholders(transactionId)) shouldBe None
        val message = "[fetchShareholders] returned nothing as key 'shareholders' was not found"
        val log = logEvents.map(l => (l.getLevel, l.getMessage)).head
        log._1.toString shouldBe "INFO"
        log._2 shouldBe message
      }
    }
  }
}
