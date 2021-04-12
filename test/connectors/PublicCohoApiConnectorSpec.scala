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



import Helpers.{LogCapturing, SCRSSpec}
import com.codahale.metrics.{Counter, Timer}
import mocks.MockMetrics
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.concurrent.Eventually
import play.api.Logger
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import utils.{DateCalculators, FeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.Future

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

    val connector = new PublicCohoApiConnector {
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
    }
  }

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


  val testCrn = "1234567890"
  "getCompanyProfile" should {


    "return some valid JSON when a valid CRN is provided and stop the timer metric" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validCompanyProfileResourceJson))))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(connector.getCompanyProfile(testCrn))
      result shouldBe Some(validCompanyProfileResourceJson)

      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"

      verify(mockTimerContext, times(1)).stop()
    }

    "report an error when receiving a 404 and isSCRS is true" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        await(connector.getCompanyProfile(testCrn, isScrs = true)) shouldBe None
        logEvents.map(_.getMessage) shouldBe List("COHO_PUBLIC_API_NOT_FOUND - Could not find company data for CRN - 1234567890")
        logEvents.size shouldBe 1
      }
      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"
    }

    "report an error when receiving a 404 and isSCRS is false" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        await(connector.getCompanyProfile(testCrn, isScrs = false)) shouldBe None
        logEvents.map(_.getMessage) shouldBe List("Could not find company data for CRN - 1234567890")
        logEvents.size shouldBe 1
      }

      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new HttpException("400",400)))

      val result = await(connector.getCompanyProfile(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"

      verify(mockTimerContext, times(1)).stop()
    }

    "report an error when receiving a Throwable exception and stop the timer metric" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new Throwable()))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(connector.getCompanyProfile(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"
    }
  }

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

  "getOfficerList" should {

    "return some valid JSON when a valid CRN is provided and stop the timer metric" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validOfficerListResourceJson))))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(connector.getOfficerList(testCrn))
      result shouldBe Some(validOfficerListResourceJson)

      urlCaptor.getValue shouldBe "stubbed/company/1234567890/officers"

      verify(mockTimerContext, times(1)).stop()
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      val result = await(connector.getOfficerList(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe  "stubbed/company/1234567890/officers"
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new HttpException("400", 400)))

      val result = await(connector.getOfficerList(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company/1234567890/officers"
    }

    "report an error when receiving a Throwable exception and stop the timer metric" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new Throwable()))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(connector.getOfficerList(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company/1234567890/officers"

      verify(mockTimerContext, times(1)).stop()
    }
  }

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

  val testOfficerId  = "123456"
  val testOfficerUrl = "/officers/_Sdjhshdsnnsi-StreatMand-greattsfh/appointments"
  "getOfficerAppointmentList" should {

    "return some valid JSON when a valid Officer ID is provided and stop the timer metric" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validOfficerAppointmentsResourceJson))))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      val result = await(connector.getOfficerAppointment(testOfficerId))
      result shouldBe validOfficerAppointmentsResourceJson

      urlCaptor.getValue shouldBe "stubbed/get-officer-appointment?fn=testFirstName&sn=testSurname"
      verify(mockTimerContext, times(1)).stop()
    }

    "generate a unique officer name when a url longer than 15 characters is passed" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validOfficerAppointmentsResourceJson))))


      val result = await(connector.getOfficerAppointment(testOfficerUrl))
      result shouldBe validOfficerAppointmentsResourceJson

      urlCaptor.getValue shouldBe "stubbed/get-officer-appointment?fn=tMand-greattsfh&sn=officersSdjhshd"
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[NotFoundException](await(connector.getOfficerAppointment(testOfficerId)))

      urlCaptor.getValue shouldBe  "stubbed/get-officer-appointment?fn=testFirstName&sn=testSurname"
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new HttpException("400", 400)))

      val ex = intercept[HttpException](await(connector.getOfficerAppointment(testOfficerId)))

      ex.responseCode shouldBe 400

      urlCaptor.getValue shouldBe "stubbed/get-officer-appointment?fn=testFirstName&sn=testSurname"
    }

    "report an error when receiving a Throwable exception and stop the timer metric" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new Throwable()))
      when(mockTimer.time()).thenReturn(mockTimerContext)

      intercept[Throwable](await(connector.getOfficerAppointment(testOfficerId)))

      urlCaptor.getValue shouldBe "stubbed/get-officer-appointment?fn=testFirstName&sn=testSurname"
      verify(mockTimerContext, times(1)).stop()
    }
  }

  "getStubbedFirstAndLastName" should {
    "return testFirstName and testSurname if string is less than 15 characters" in new Setup{
      val (firstname, lastname) =  connector.getStubbedFirstAndLastName(testOfficerId)
      firstname shouldBe "testFirstName"
      lastname shouldBe "testSurname"
    }

    "return a dynamic name if string is less than 15 characters" in new Setup{
      val (firstname, lastname) =  connector.getStubbedFirstAndLastName(testOfficerUrl)
      firstname shouldBe "tMand-greattsfh"
      lastname shouldBe "officersSdjhshd"
    }
  }

  "createAPIAuthHeader" should {

    "return a HeaderCarrier with the correct Basic auth token" when {
     "a request is made by an allowListed service" in new Setup {
       connector.createAPIAuthHeader().authorization shouldBe Some(Authorization("Basic Q29ob1B1YmxpY1Rva2Vu"))
     }
     "a request is made by an un-allowlisted service" in new Setup {
       connector.createAPIAuthHeader(isScrs = false).authorization shouldBe Some(Authorization("Basic Tm9uU0NSU0NvaG9QdWJsaWNUb2tlbg=="))
     }
    }
  }

}