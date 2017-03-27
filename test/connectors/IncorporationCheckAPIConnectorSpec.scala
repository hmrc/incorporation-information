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
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.libs.json.Json
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import utils.{FeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.Future

class IncorporationCheckAPIConnectorSpec extends SCRSSpec {

  val testProxyUrl = "testIIUrl"
  implicit val hc = HeaderCarrier()

  val mockWSHttp = mock[WSHttp]
  val mockWSHttpProxy = mock[WSHttp with WSProxy]

  trait Setup {
    val connector = new IncorporationCheckAPIConnector {
      val cohoAPIStubUrl = "testIIUrl/internal/check-submission"
      val cohoAPIUrl = "b"
      val cohoApiAuthToken = "c"
      val itemsToFetch = "1"
      val httpNoProxy = mockWSHttp
      val httpProxy = mockWSHttpProxy
      val featureSwitch: SCRSFeatureSwitches = new SCRSFeatureSwitches {
        override val KEY_TX_API = "x"
        override val KEY_INCORP_UPDATE = "y"
        override val transactionalAPI = FeatureSwitch(KEY_TX_API, false)
        override val scheduler = FeatureSwitch(KEY_INCORP_UPDATE, false)
      }
    }
  }

  val validSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"9999999999",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2016-08-10",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
      |}
      |}""".stripMargin)

  val validSubmissionResponseItems = Seq(
    IncorpUpdate(
      "7894578956784",
      "accepted",
      Some("9999999999"),
      Some(new DateTime(2016, 8, 10, 0, 0)),
      "123456789")
  )

  "checkForIncorpUpdate" should {

    val testTimepoint = UUID.randomUUID().toString

    "return a submission status response when no timepoint is provided" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validSubmissionResponseJson))))

      val result = await(connector.checkForIncorpUpdate())
      result shouldBe validSubmissionResponseItems

      urlCaptor.getValue shouldBe url
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?timepoint=$testTimepoint&items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validSubmissionResponseJson))))

      val result = await(connector.checkForIncorpUpdate(Some(testTimepoint)))
      result shouldBe validSubmissionResponseItems

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream4xx" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream5xx" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("502", 502, 502)))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an unexpected error" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }
  }
}
