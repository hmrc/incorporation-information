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

import java.util.UUID

import Helpers.SCRSSpec
import models.IncorpUpdate
import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import utils.{FeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.Future

class PublicCohoApiConnectorSpec extends SCRSSpec {

  val testProxyUrl = "testIIUrl/incorporation-frontend-stubs"
  implicit val hc = HeaderCarrier()

  val mockHttp = mock[WSHttp]
  val mockHttpProxy = mock[WSHttp with WSProxy]

  val stubPublicUrlValue = "testIIUrl/incorporation-frontend-stubs"
  val cohoPublicUrlValue = "http://test.url.for.companieshouse.publicapi"

  class Setup {

    reset(mockHttp, mockHttpProxy)

    val connector = new PublicCohoApiConn {
      //      val stubBaseUrl = stubUrlValue
      //      val cohoBaseUrl = cohoUrlValue
      val incorpFrontendStubUrl = "incorp FE Stub"
      val cohoPublicUrl = "Coho public url"
      val cohoPublicApiAuthToken = "CohoPublicToken"
      override val cohoStubbedUrl = "stubbed"
      //      val cohoApiAuthToken = "c"
      //      val itemsToFetch = "1"
      override val httpNoProxy = mockHttp
      override val httpProxy = mockHttpProxy
      override val featureSwitch: SCRSFeatureSwitches = new SCRSFeatureSwitches {
        override val KEY_FIRE_SUBS = "w"
        override val KEY_TX_API = "x"
        override val KEY_INCORP_UPDATE = "y"
        override val KEY_SCHED_METRICS = "z"
        override val transactionalAPI = FeatureSwitch(KEY_TX_API, false)
        override val scheduler = FeatureSwitch(KEY_INCORP_UPDATE, false)
      }
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


    "return some valid JSON when a valid CRN is provided" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validCompanyProfileResourceJson))))

      val result = await(connector.getCompanyProfile(testCrn))
      result shouldBe Some(validCompanyProfileResourceJson)

            urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      val result = await(connector.getCompanyProfile(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new HttpException("400",400)))

      val result = await(connector.getCompanyProfile(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company-profile/1234567890"
    }

    "report an error when receiving a Throwable exception" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new Throwable()))

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

    "return some valid JSON when a valid CRN is provided" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validOfficerListResourceJson))))

      val result = await(connector.getOfficerList(testCrn))
      result shouldBe Some(validOfficerListResourceJson)

      urlCaptor.getValue shouldBe "stubbed/company/1234567890/officers"
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      val result = await(connector.getOfficerList(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe  "stubbed/company/1234567890/officers"
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new HttpException("400", 400)))

      val result = await(connector.getOfficerList(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company/1234567890/officers"
    }

    "report an error when receiving a Throwable exception" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new Throwable()))

      val result = await(connector.getOfficerList(testCrn))

      result shouldBe None

      urlCaptor.getValue shouldBe "stubbed/company/1234567890/officers"
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

  val testOfficerId = "123456"
  "getOfficerAppointmentList" should {

    "return some valid JSON when a valid Officer ID is provided" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validOfficerAppointmentsResourceJson))))

      val result = await(connector.getOfficerAppointment(testOfficerId))
      result shouldBe Some(validOfficerAppointmentsResourceJson)

      urlCaptor.getValue shouldBe "123456"
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      val result = await(connector.getOfficerAppointment(testOfficerId))

      result shouldBe None

      urlCaptor.getValue shouldBe  "123456"
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new HttpException("400", 400)))

      val result = await(connector.getOfficerAppointment(testOfficerId))

      result shouldBe None

      urlCaptor.getValue shouldBe "123456"
    }

    "report an error when receiving a Throwable exception" in new Setup {
      val url = s"$cohoPublicUrlValue"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new Throwable()))

      val result = await(connector.getOfficerAppointment(testOfficerId))

      result shouldBe None

      urlCaptor.getValue shouldBe "123456"
    }
  }
}