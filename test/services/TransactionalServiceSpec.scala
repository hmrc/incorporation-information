/*
 * Copyright 2023 HM Revenue & Customs
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

import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import Helpers.SCRSSpec
import connectors.{FailedTransactionalAPIResponse, IncorporationAPIConnector, PublicCohoApiConnectorImpl, SuccessfulTransactionalAPIResponse}
import models.IncorpUpdate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.Logger
import play.api.libs.json._
import play.api.test.Helpers._
import repositories.IncorpUpdateRepository
import utils.TimestampFormats

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TransactionalServiceSpec extends SCRSSpec with LogCapturing {

  val mockConnector = mock[IncorporationAPIConnector]
  val mockRepos = mock[IncorpUpdateRepository]
  val mockCohoConnector = mock[PublicCohoApiConnectorImpl]

  class Setup {
    object Service extends TransactionalService {
      override protected val connector = mockConnector
      override val incorpRepo = mockRepos
      override val publicCohoConnector = mockCohoConnector

    }
  }

  class SetupForCohoTransform {
    object Service extends TransactionalService {
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

  val dateTime = Json.toJson(LocalDateTime.parse("2017-05-15T17:45:45Z", TimestampFormats.ldtFormatter))(TimestampFormats.milliDateTimeFormat)

  "extractJson" must {

    "fetch a json sub-document from a successful TransactionalAPIResponse by the supplied key" in new Setup {
      val json = buildJson()
      val transformer = (JsPath \ "officers").json.prune

      val response = SuccessfulTransactionalAPIResponse(json)

      await(Service.extractJson(Future.successful(response), transformer)) mustBe Some(companyProfileJson)
    }

    "return a None when attempting to fetch a sub-document from a successful TransactionalAPIResponse with an un-matched transformer key" in new Setup {
      val json = buildJson()
      val transformer = (JsPath \ "unmatched").json.prune

      val response = SuccessfulTransactionalAPIResponse(json)

      await(Service.extractJson(Future.successful(response), transformer)) mustBe None
    }

    "return a None when a FailedTransactionalAPIResponse is returned" in new Setup {
      val response = FailedTransactionalAPIResponse
      val transformer = (JsPath \ "officers").json.prune

      await(Service.extractJson(Future.successful(response), transformer)) mustBe None
    }
  }

  "fetchCompanyProfile from transactional api" must {

    "return some Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(buildJson())
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))
      await(Service.fetchCompanyProfileFromTx(transactionId)) mustBe Some(companyProfileJson)
    }

    "return None from transactional api" when {

      "a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
        val response = FailedTransactionalAPIResponse
        when(mockConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(Service.fetchCompanyProfileFromTx(transactionId)) mustBe None
      }
    }
  }

  "fetchOfficerList" must {

    val crn = "crn-12345"

    val incorpUpdate = IncorpUpdate(transactionId, "accepted", Some(crn), Some(LocalDateTime.parse("2018-05-01", TimestampFormats.ldtFormatter)), "", None)

    "return Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(buildJson())
      when(mockRepos.getIncorpUpdate(any())).thenReturn(Future.successful(None))
      when(mockConnector.fetchTransactionalData(any())(any()))
        .thenReturn(Future.successful(response))

      await(Service.fetchOfficerList(transactionId)) mustBe officersJson
    }

    "return FailedToFetchOfficerListFromTxAPI when a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
      val response = FailedTransactionalAPIResponse
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))

      intercept[FailedToFetchOfficerListFromTxAPI](await(Service.fetchOfficerList(transactionId)))
    }

    "return a JsResultException when a SuccessfulTransactionalAPIResponse is returned for the supplied transaction Id but an incorrect json document is provided" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(incorrectJson)
      when(mockConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))

      intercept[JsResultException](await(Service.fetchOfficerList(transactionId)))
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

      await(Service.fetchOfficerList(transactionId)) mustBe expected
    }
  }

  "checkIfCompIncorporated" must {

    "return Some(String) - the crn" when {

      "Company exists and has a crn" in new Setup {
        val incorpUpdate = IncorpUpdate("transId", "accepted", Some("foo"), None, "", None)
        when(mockRepos.getIncorpUpdate(ArgumentMatchers.any[String])).thenReturn(Future.successful(Some(incorpUpdate)))

        await(Service.checkIfCompIncorporated("fooBarTest")) mustBe Some("foo")
      }
    }

    "return None" when {

      "Company exists, has a status != rejected but has no crn" in new Setup {
        val incorpUpdate = IncorpUpdate("transId", "foo", None, None, "", None)
        when(mockRepos.getIncorpUpdate(ArgumentMatchers.any[String])).thenReturn(Future.successful(Some(incorpUpdate)))

        await(Service.checkIfCompIncorporated("fooBarTest")) mustBe None
      }

      "Company does not exist" in new Setup {
        when(mockRepos.getIncorpUpdate(ArgumentMatchers.any[String])).thenReturn(Future.successful(None))
        await(Service.checkIfCompIncorporated("fooBarTest")) mustBe None
      }
    }
  }

  "fetchCompanyProfileFromCoho" must {

    "return None when companyProfile cannot be found" in new Setup {
      val response = FailedTransactionalAPIResponse
      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any())).thenReturn(Future.successful(None))
      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any())).thenReturn(Future.successful(response))
      await(Service.fetchCompanyProfileFromCoho("num", "")) mustBe None
    }

    "return Some(json) when companyProfile can be found" in new SetupForCohoTransform {
      val json = buildJson("foo")
      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any())).thenReturn(Future.successful(Some(Service.jsonObj)))
      await(Service.fetchCompanyProfileFromCoho("num", "")) mustBe Some(Service.jsonObj)
    }
  }

  "transformDataFromCoho" must {

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

      Service.transformDataFromCoho(input) mustBe Some(expected)
    }

    "return a transformed officer appointment Json with named_elements and without named_elements" in new Setup {

      val testJson1 = "/officers/testJson1/appointments"
      val testJson2 = "/officers/testJson2/appointments"
      val testJson3 = "/officers/testJson3/appointments"

      val officerAppointmentJson1 = Json.parse(
        s"""
         {
           |   "name":"test name",
           |   "etag":"34bdb219bbfd6833bc2b72d746d0c7eeeadeb1e1",
           |   "items_per_page":35,
           |   "links":{
           |      "self":"${testJson1}"
           |   },
           |   "is_corporate_officer":false,
           |   "kind":"personal-appointment",
           |   "total_results":1,
           |   "date_of_birth":{
           |      "month":7,
           |      "year":1999
           |   },
           |   "items":[
           |      {
           |         "occupation":"Director",
           |         "nationality":"South Korean",
           |         "country_of_residence":"South Korea",
           |         "former_names":[
           |            {
           |               "surname":"test surname"
           |            }
           |         ],
           |         "name":"test name",
           |         "name_elements":{
           |            "surname":"testSurname",
           |            "title":"Ms",
           |            "forename":"test"
           |         },
           |         "appointed_on":"2023-03-08",
           |         "address":{
           |            "country":"United Kingdom",
           |            "premises":"test 12",
           |            "locality":"London",
           |            "address_line_1":"test line 1",
           |            "postal_code":"AA1A 1AA"
           |         },
           |         "officer_role":"director",
           |         "links":{
           |            "company":"/company/111111111"
           |         },
           |         "appointed_to":{
           |            "company_number":"111111111",
           |            "company_status":"active",
           |            "company_name":"Test UK LTD"
           |         }
           |      }
           |   ],
           |   "start_index":0
           |}
           |
        """.stripMargin)

      val officerAppointmentJson2 = Json.parse(
        s"""
           |{
           |   "kind":"personal-appointment",
           |   "start_index":0,
           |   "items":[
           |      {
           |         "appointed_to":{
           |            "company_number":"111111111",
           |            "company_name":"Test UK LTD",
           |            "company_status":"active"
           |         },
           |         "address":{
           |            "country":"United Kingdom",
           |            "address_line_1":"test address line 1",
           |            "premises":"Suite Test ",
           |            "locality":"London",
           |            "postal_code":"AA1A 8AA"
           |         },
           |         "nationality":"South Korean",
           |         "name":"test name",
           |         "country_of_residence":"England",
           |         "links":{
           |            "company":"/company/111111111"
           |         },
           |         "former_names":[
           |            {
           |               "surname":"test surname"
           |            }
           |         ],
           |         "appointed_on":"2023-03-08",
           |         "officer_role":"director",
           |         "name_elements":{
           |            "title":"Mr",
           |            "surname":"test",
           |            "forename":"testFore"
           |         },
           |         "occupation":"Director"
           |      }
           |   ],
           |   "etag":"3b8d1397df3fca624c84cdf99ed9fa2875b4ed2f",
           |   "items_per_page":35,
           |   "name":"test name",
           |   "links":{
           |      "self":"${testJson2}"
           |   },
           |   "total_results":1,
           |   "is_corporate_officer":false,
           |   "date_of_birth":{
           |      "year":1990,
           |      "month":10
           |   }
           |}
        """.stripMargin)


      val officerAppointmentJson3 = Json.parse(
        s"""
        {
           "is_corporate_officer":true,
           "start_index":0,
           "etag":"57d943bacf0e565028890163271a428c49284a14",
           "name":"Test name",
           "total_results":1,
           "links":{
              "self":"${testJson3}"
           },
           "kind":"personal-appointment",
           "items":[
              {
                 "appointed_to":{
                    "company_name":"test UK LTD",
                    "company_number":"111111111",
                    "company_status":"active"
                 },
                 "links":{
                    "company":"/company/111111111"
                 },
                 "officer_role":"corporate-director",
                 "name":"Test name",
                 "address":{
                    "country":"South Korea",
                    "postal_code":"11111",
                    "locality":"Seoul",
                    "premises":"10A",
                    "address_line_1":"test address line 1"
                 },
                 "appointed_on":"2023-03-08",
                 "identification":{
                    "legal_form":"INCORPORATION",
                    "identification_type":"other-corporate-body-or-firm",
                    "legal_authority":"SOUTH KOREA",
                    "registration_number":"111-11-11111",
                    "place_registered":"SOUTH KOREA"
                 }
              }
           ],
           "items_per_page":35
        }
        """.stripMargin)


      val officerListJson = Json.parse(
        s"""
           |{
           |   "items_per_page":35,
           |   "total_results":3,
           |   "items":[
           |      {
           |         "date_of_birth":{
           |            "year":1979,
           |            "month":7
           |         },
           |         "nationality":"South Korean",
           |         "officer_role":"director",
           |         "name":"test name",
           |         "address":{
           |            "postal_code":"AA1A 8AA",
           |            "locality":"London",
           |            "country":"United Kingdom",
           |            "premises":"Suite 1111",
           |            "address_line_1":"test address line 1"
           |         },
           |         "former_names":[
           |            {
           |               "surname":"test surname"
           |            }
           |         ],
           |         "occupation":"Director",
           |         "country_of_residence":"South Korea",
           |         "links":{
           |            "self":"/company/111111111/appointments/U-3u2qpboQWbaUk27jikP1CgsP4",
           |            "officer":{
           |               "appointments":"${testJson1}"
           |            }
           |         },
           |         "appointed_on":"2023-03-08"
           |      },
           |      {
           |         "address":{
           |            "premises":"Suite 1111",
           |            "locality":"London",
           |            "country":"United Kingdom",
           |            "postal_code":"AA1A 8AA",
           |            "address_line_1":"address line 1"
           |         },
           |         "name":"test, name",
           |         "officer_role":"director",
           |         "former_names":[
           |            {
           |               "surname":"test surname"
           |            }
           |         ],
           |         "nationality":"South Korean",
           |         "date_of_birth":{
           |            "year":1990,
           |            "month":10
           |         },
           |         "links":{
           |            "officer":{
           |               "appointments":"${testJson2}"
           |            },
           |            "self":"/company/111111111/appointments/rKGhKhGmo0XM64KJ17Z_xLi7cVE"
           |         },
           |         "appointed_on":"2023-03-08",
           |         "country_of_residence":"England",
           |         "occupation":"Director"
           |      },
           |      {
           |         "identification":{
           |            "registration_number":"111-11-11111",
           |            "legal_authority":"SOUTH KOREA",
           |            "place_registered":"SOUTH KOREA",
           |            "legal_form":"INCORPORATION",
           |            "identification_type":"other-corporate-body-or-firm"
           |         },
           |         "appointed_on":"2023-03-08",
           |         "links":{
           |            "self":"/company/14716412/appointments/n74TvHvLocUHo8p75wzfQEiN7aY",
           |            "officer":{
           |               "appointments":"${testJson3}"
           |            }
           |         },
           |         "name":"testName",
           |         "officer_role":"corporate-director",
           |         "address":{
           |            "country":"South Korea",
           |            "locality":"Seoul",
           |            "premises":"11A",
           |            "address_line_1":"test address line 1",
           |            "postal_code":"11111"
           |         }
           |      }
           |   ],
           |   "kind":"officer-list",
           |   "etag":"07a86b3e11f19bac3a17b5fbfbad0a92f09c45f5",
           |   "inactive_count":0,
           |   "start_index":0,
           |   "links":{
           |      "self":"/company/111111111/officers"
           |   },
           |   "active_count":3,
           |   "resigned_count":0
           |}
        """.stripMargin)

      val expectedJson = Json.parse(
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


      when(mockCohoConnector.getOfficerAppointment(eqTo(testJson1))(any()))
        .thenReturn(Future.successful(officerAppointmentJson1))

      when(mockCohoConnector.getOfficerAppointment(eqTo(testJson2))(any()))
        .thenReturn(Future.successful(officerAppointmentJson2))

      when(mockCohoConnector.getOfficerAppointment(eqTo(testJson3))(any()))
        .thenReturn(Future.successful(officerAppointmentJson3))


      when(mockCohoConnector.getOfficerList(any())(any()))
        .thenReturn(Future.successful(Some(officerListJson)))

      val result = await(Service.fetchOfficerListFromPublicAPI(transactionId, crn))

      result mustBe expectedJson
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

      Service.transformDataFromCoho(input) mustBe Some(expected)
    }

  }
  "sicCodesConverter" must {
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
      Service.sicCodesConverter(sicCodes) mustBe Some(listOfJsObjects)


    }
  }

  "sicCodesConverter" must {
    "return None when None is passed in" in new Setup {
      Service.sicCodesConverter(None) mustBe None
    }
  }

  "transformOfficerAppointment" must {

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
      val result = Service.transformOfficerAppointment(officerAppointmentJson)
      result mustBe Some(expected)
    }

    "return a None if the key 'name_elements' cannot be found in the supplied Json" in new Setup {
      val incorrectJson = Json.parse("""{"test":"json"}""")
      intercept[NoItemsFoundException](Service.transformOfficerAppointment(incorrectJson))
    }
    "correctly return None if items can be found but Name elements cannot be found" in new Setup {
      val res = Service.transformOfficerAppointment(Json.parse("""{"items":[{"foo":"bar"}]}"""))
      res mustBe None
    }
  }

  "fetchOfficerAppointment" must {

    val url = "test/url"

    "return a transformed officer appointment Json" in new Setup {
      when(mockCohoConnector.getOfficerAppointment(eqTo(url))(any()))
        .thenReturn(Future.successful(officerAppointmentJson))

      Service.fetchOfficerAppointment(url)
    }

    "return None when an officer appointment cannot be found in the public API" in new Setup {
      when(mockCohoConnector.getOfficerAppointment(eqTo(url))(any()))
        .thenReturn(Future.successful(Json.obj()))

      Service.fetchOfficerAppointment(url)
    }

    "return None when the returned officer appointment cannot be transformed" in new Setup {
      val incorrectJson = Json.parse("""{"test":"json"}""")
      when(mockCohoConnector.getOfficerAppointment(eqTo(url))(any()))
        .thenReturn(Future.successful(incorrectJson))

      Service.fetchOfficerAppointment(url)
    }
  }

  "transformOfficerList" must {

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

      val result = Service.transformOfficerList(publicOfficerJson)
      result mustBe expected
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

      val result = Service.transformOfficerList(publicOfficerJsonWithoutCountry)
      result mustBe expected
    }
  }

  "fetchOfficerListFromPublicAPI" must {

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

      val result = await(Service.fetchOfficerListFromPublicAPI(transactionId, crn))
      result mustBe expected
    }

    "return an officer list without name elements when none are provided by the public API" in new Setup {

      val expected = Json.parse(
        s"""
           |{
           |  "officers" : []
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

      val result = await(Service.fetchOfficerListFromPublicAPI(transactionId, crn))
      result mustBe expected
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

      val result = await(Service.fetchOfficerListFromPublicAPI(transactionId, crn))
      result mustBe expected
    }
  }

  "fetchSicCodes" must {

    "return None if no json was returned by the public API" in new Setup {
      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      await(Service.fetchSicCodes(crn, usePublicAPI = true)) mustBe None
    }

    "return None if no json was returned by the transactional API" in new Setup {
      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      await(Service.fetchSicCodes(crn, usePublicAPI = false)) mustBe None
    }

    "return None if no SIC codes were returned by the public API" in new Setup {
      val unexpectedResponse = Json.parse("""{ "foo":"bar" }""")

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(unexpectedResponse)))

      await(Service.fetchSicCodes(crn, usePublicAPI = true)) mustBe None
    }

    "return None if no SIC codes were returned by the transactional API" in new Setup {
      val unexpectedResponse = Json.parse("""{ "foo":"bar" }""")

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(unexpectedResponse)))

      await(Service.fetchSicCodes(crn, usePublicAPI = false)) mustBe None
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

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(Service.fetchSicCodes(crn, usePublicAPI = true)) mustBe Some(expectedCodes)
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

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(expectedJson)))

      await(Service.fetchSicCodes(transactionId, usePublicAPI = false)) mustBe Some(expectedCodes)
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

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(Service.fetchSicCodes(crn, usePublicAPI = true)) mustBe None
    }
  }

  "fetchSicCodesByCRN" must {
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
      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(Service.fetchSicByCRN(crn)) mustBe Some(expectedCodes)
    }
    "return no SIC codes" in new Setup {
      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      await(Service.fetchSicByCRN(crn)) mustBe None
    }
  }

  "fetchSicCodesByCRN" must {
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

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(Some(expectedJson)))

      await(Service.fetchSicByTransId(transactionId)) mustBe Some(expectedCodes)
    }

    "return some SIC codes from the internal API if an incorporation update exists but the Public API returned nothing" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(true))

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(transactionalJson)))

      await(Service.fetchSicByTransId(transactionId)) mustBe Some(expectedCodes)
    }

    "return no SIC codes from the either API if an incorporation update exists but both APIs returned nothing" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(true))

      when(mockCohoConnector.getCompanyProfile(ArgumentMatchers.any[String], any())(any()))
        .thenReturn(Future.successful(None))

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      await(Service.fetchSicByTransId(transactionId)) mustBe None
    }

    "return no SIC codes from the internal API if an incorporation update doesn't exist" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(false))

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      await(Service.fetchSicByTransId(transactionId)) mustBe None
    }

    "return SIC codes from the internal API if an incorporation update doesn't exist" in new Setup {
      when(mockRepos.getIncorpUpdate(eqTo(transactionId)))
        .thenReturn(incorporated(false))

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      await(Service.fetchSicByTransId(transactionId)) mustBe None
    }
  }
  "fetchShareholders" must {
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

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(txJsonContainingShareholders)))
      withCaptureOfLoggingFrom(Service.logger) { logEvents =>
        await(Service.fetchShareholders(transactionId)) mustBe Some(extractedJson)
        val log =  logEvents.map(l => (l.getLevel, l.getMessage)).head
        log._1.toString mustBe "INFO"
        log._2 mustBe s"[Service][fetchShareholders] returned an array with the size - 1"
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
      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(txJsonContainingEmptyListOfShareholders)))
      withCaptureOfLoggingFrom(Service.logger) { logEvents =>
        await(Service.fetchShareholders(transactionId)) mustBe Some(extractedJson)
        val log = logEvents.map(l => (l.getLevel, l.getMessage)).head
        log._1.toString mustBe "WARN"
        log._2 mustBe s"[Service][fetchShareholders] returned an array with the size - 0"
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

      when(mockConnector.fetchTransactionalData(ArgumentMatchers.any[String])(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(txJsonContainingNOShareholdersKey)))
      withCaptureOfLoggingFrom(Service.logger) { logEvents =>
        await(Service.fetchShareholders(transactionId)) mustBe None
        val log = logEvents.map(l => (l.getLevel, l.getMessage)).head
        log._1.toString mustBe "INFO"
        log._2 mustBe s"[Service][fetchShareholders] returned nothing as key 'shareholders' was not found"
      }
    }
  }
}
