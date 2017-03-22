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
import config.WSHttp
import models.{IncorpUpdate, IncorpUpdatesResponse}
import org.joda.time.DateTime
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito._
import uk.gov.hmrc.play.http._

import scala.concurrent.Future

// TODO - II-INCORP - Also needs (?might be better as?) an IT with wiremock
class IncorporationCheckAPIConnectorSpec extends SCRSSpec {

  val testProxyUrl = "testIIUrl"
  implicit val hc = HeaderCarrier()

  val mockWSHttp = mock[WSHttp]

  trait Setup {
    val connector = new IncorporationCheckAPIConnector {
      val proxyUrl = testProxyUrl
      val http = mockWSHttp
    }
  }

  val validSubmissionResponse = IncorpUpdatesResponse(
    Seq(
      IncorpUpdate(
        "transactionId",
        "status",
        Some("crn"),
        Some(new DateTime(2016, 8, 10, 0, 0)),
        "100000011")
    ),
    "testNextLink")

  "IncorporationCheckAPIConnector" should {
    "use the correct http" in {
      IncorporationCheckAPIConnector.http shouldBe WSHttp
    }
    "use the correct proxyUrl" in {
      IncorporationCheckAPIConnector.proxyUrl shouldBe "http://localhost:9986"
    }
  }

  "checkSubmission" should {

    val testTimepoint = UUID.randomUUID().toString

    "return a submission status response when no timepoint is provided" in new Setup {
      val testTimepoint = UUID.randomUUID().toString

      when(mockWSHttp.GET[IncorpUpdatesResponse](Matchers.anyString())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission()) shouldBe validSubmissionResponse
    }

    "return a submission status response when a timepoint is provided" in new Setup {
      val testTimepoint = UUID.randomUUID().toString

      when(mockWSHttp.GET[IncorpUpdatesResponse](Matchers.anyString())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint))) shouldBe validSubmissionResponse
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?timepoint=$testTimepoint&items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(Some(testTimepoint)))

      urlCaptor.getValue shouldBe url
    }

    "verify nothing is appended as a query string if a timepoint is not supplied" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(validSubmissionResponse))

      await(connector.checkSubmission(None))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream4xx" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream5xx" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("502", 502, 502)))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an unexpected error" in new Setup {
      val url = s"$testProxyUrl/internal/check-submission?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockWSHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[SubmissionAPIFailure](await(connector.checkSubmission(None)))

      urlCaptor.getValue shouldBe url
    }
  }
}
