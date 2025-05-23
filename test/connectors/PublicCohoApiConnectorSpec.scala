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

package connectors


import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import Helpers.SCRSSpec
import com.codahale.metrics.{Counter, Timer}
import mocks.MockMetrics
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import utils.{DateCalculators, FeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class PublicCohoApiConnectorSpec extends SCRSSpec with LogCapturing with Eventually {

  val testProxyUrl = "testIIUrl/incorporation-frontend-stubs"
  implicit val hc = HeaderCarrier()

  trait WsHttpWithProxy extends CoreGet with CorePut with CorePost with WSProxy

  val mockHttp = mock[WSHttp]
  val mockHttpProxy = mock[WsHttpWithProxy]
  val mockMetrics = new MockMetrics
  val mockTimer = mockMetrics.mockTimer
  val mockSuccessCounter = new Counter
  val mockFailureCounter = new Counter
  val mockTimerContext = mock[Timer.Context]

  val stubPublicUrlValue = "testIIUrl/incorporation-frontend-stubs"
  val cohoPublicUrlValue = "http://test.url.for.companieshouse.publicapi"

  class Setup {

    reset(mockHttp, mockHttpProxy, mockTimerContext)

    object Connector extends PublicCohoApiConnector {
      override val dateCalculators: DateCalculators = new DateCalculators {}
      val incorpFrontendStubUrl = "incorp FE Stub"
      val cohoPublicUrl = "Coho public url"
      val cohoPublicApiAuthToken = "CohoPublicToken"
      val nonSCRSPublicApiAuthToken = "NonSCRSCohoPublicToken"
      override val cohoStubbedUrl = "stubbed"
      override val httpNoProxy = mockHttp
      override val httpProxy = mockHttpProxy
      override val featureSwitch: SCRSFeatureSwitches = new SCRSFeatureSwitches {
        override val KEY_FIRE_SUBS = "w"
        override val KEY_TX_API = "x"
        override val KEY_INCORP_UPDATE = "y"
        override val KEY_SCHED_METRICS = "z"
        override val transactionalAPI = FeatureSwitch(KEY_TX_API, false)
        override val scheduler = FeatureSwitch(KEY_INCORP_UPDATE, false)
        override val KEY_PRO_MONITORING = "p"
      }
      override val metrics: MetricsService = mockMetrics
      override val successCounter: Counter = metrics.publicCohoApiSuccessCounter
      override val failureCounter: Counter = metrics.publicCohoApiFailureCounter
      override protected val loggingDays = "MON,TUE,WED,THU,FRI"
      override protected val loggingTimes = "08:00:00_17:00:00"
      override implicit val ec: ExecutionContext = global
    }
  }

  val testCrn = "1234567890"
  val testOfficerId = "123456"
  val testOfficerUrl = "/officers/_Sdjhshdsnnsi-StreatMand-greattsfh/appointments"

  "getCompanyProfile" must {

    val validCompanyProfileResourceJson = Json.parse(
      """{
        |   "company_name" : "MOOO LIMITED",
        |   "company_number" : "1234567890",
        |   "company_status" : "Accepted",
        |   "registered_office_address" : {
        |      "address_line_1" : "The road",
        |      "address_line_2" : "GORING-BY-SEA",
        |      "care_of" : "Bob",
        |      "country" : "United Kingdom",
        |      "locality" : "Worthing",
        |      "po_box" : "123",
        |      "postal_code" : "AA12 6ZZ",
        |      "premises" : "21",
        |      "region" : "Regionshire"
        |   },
        |   "sic_codes" : [
        |      {"sic_code" : "84240"},
        |      {"sic_code" : "01410"}
        |   ],
        |   "type" : "ltd"
        |}""".stripMargin)

    val url = "stubbed/company-profile/1234567890"

    "return some valid JSON when a valid CRN is provided and stop the timer metric" in new Setup {
      when(mockHttp.GET[Option[JsValue]](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validCompanyProfileResourceJson)))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(Connector.getCompanyProfile(testCrn))
      result mustBe Some(validCompanyProfileResourceJson)

      verify(mockTimerContext, times(1)).stop()
    }

    "report an error when receiving a Throwable exception and stop the timer metric" in new Setup {
      when(mockHttp.GET[Option[JsValue]](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bang")))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(Connector.getCompanyProfile(testCrn))

      result mustBe None
    }
  }

  "getOfficerList" must {

    val validOfficerListResourceJson = Json.parse(
      """{
        |   "items" : [
        |      {
        |         "address" : {
        |            "address_line_1" : "The Road",
        |            "address_line_2" : "The Street",
        |            "care_of" : "John",
        |            "country" : "U.K.",
        |            "locality" : "Shropshire",
        |            "po_box" : "4",
        |            "postal_code" : "TF4 4TF",
        |            "premises" : "33",
        |            "region" : "Midlands"
        |         },
        |         "date_of_birth" : {
        |            "month" : 11,
        |            "year" : 1980
        |         },
        |         "links" : {
        |            "officer" : {
        |               "appointments" : "test"
        |            }
        |         },
        |         "name" : "Tom Tom",
        |         "officer_role" : "Director",
        |         "resigned_on" : "2014-01-01T23:28:56.782Z"
        |      }
        |   ]
        |}""".stripMargin)

    val url = "stubbed/company/1234567890/officers"

    "return some valid JSON when a valid CRN is provided and stop the timer metric" in new Setup {
      when(mockHttp.GET[Option[JsValue]](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(validOfficerListResourceJson)))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(Connector.getOfficerList(testCrn))
      result mustBe Some(validOfficerListResourceJson)

      verify(mockTimerContext, times(1)).stop()
    }

    "report an error when receiving a Throwable exception and stop the timer metric" in new Setup {
      when(mockHttp.GET[HttpResponse](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bang")))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(Connector.getOfficerList(testCrn))

      result mustBe None

      verify(mockTimerContext, times(1)).stop()
    }
  }

  "getOfficerAppointmentList" must {

    val validOfficerAppointmentsResourceJson = Json.parse(
      """{
        |   "items" : [
        |      {
        |         "name_elements" : {
        |            "forename" : "Bob",
        |            "honours" : "MBE",
        |            "other_forenames" : "John",
        |            "surname" : "Thomas",
        |            "title" : "Mr"
        |         }
        |      }
        |   ]
        |}""".stripMargin)

    val url = "stubbed/get-officer-appointment?fn=testFirstName&sn=testSurname"

    "return some valid JSON when a valid Officer ID is provided and stop the timer metric" in new Setup {
      when(mockHttp.GET[JsValue](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validOfficerAppointmentsResourceJson))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(Connector.getOfficerAppointment(testOfficerId))
      result mustBe validOfficerAppointmentsResourceJson

      verify(mockTimerContext, times(1)).stop()
    }

    "generate a unique officer name when a url longer than 15 characters is passed" in new Setup {
      val url = "stubbed/get-officer-appointment?fn=tMand-greattsfh&sn=officersSdjhshd"

      when(mockHttp.GET[JsValue](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validOfficerAppointmentsResourceJson))


      val result = await(Connector.getOfficerAppointment(testOfficerUrl))
      result mustBe validOfficerAppointmentsResourceJson
    }

    "report an error when receiving a Throwable exception and stop the timer metric" in new Setup {
      when(mockHttp.GET[JsValue](ArgumentMatchers.eq(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new Exception("bang")))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      intercept[Exception](await(Connector.getOfficerAppointment(testOfficerId)))

      verify(mockTimerContext, times(1)).stop()
    }
  }

  "getStubbedFirstAndLastName" must {
    "return testFirstName and testSurname if string is less than 15 characters" in new Setup {
      val (firstname, lastname) = Connector.getStubbedFirstAndLastName(testOfficerId)
      firstname mustBe "testFirstName"
      lastname mustBe "testSurname"
    }

    "return a dynamic name if string is less than 15 characters" in new Setup {
      val (firstname, lastname) = Connector.getStubbedFirstAndLastName(testOfficerUrl)
      firstname mustBe "tMand-greattsfh"
      lastname mustBe "officersSdjhshd"
    }
  }

  "createAPIAuthHeader" must {
    "return a Header with the correct Basic auth token" when {
      "a request is made by an allowListed service" in new Setup {
        Connector.createAPIAuthHeader() mustBe Seq("Authorization" -> "Basic Q29ob1B1YmxpY1Rva2Vu")
      }
      "a request is made by an un-allowlisted service" in new Setup {
        Connector.createAPIAuthHeader(isScrs = false) mustBe Seq("Authorization" -> "Basic Tm9uU0NSU0NvaG9QdWJsaWNUb2tlbg==")
      }
    }
  }
}
