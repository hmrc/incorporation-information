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

import Helpers.SCRSSpec
import com.codahale.metrics.{Counter, Timer}
import mocks.MockMetrics
import models.{IncorpUpdate, IncorpUpdatesResponse}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import utils.{DateCalculators, FeatureSwitch, SCRSFeatureSwitches}

import java.time.format.DateTimeParseException
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class IncorporationCheckAPIConnectorSpec extends SCRSSpec {

  val mockIncorporationCheckAPIConnector = mock[IncorporationAPIConnector]
  val testProxyUrl = "testIIUrl/incorporation-frontend-stubs"
  implicit val hc = HeaderCarrier()

  trait WsHttpWithProxy extends CoreGet with CorePut with CorePost with WSProxy

  val mockHttp = mock[WSHttp]
  val mockHttpProxy = mock[WsHttpWithProxy]

  val stubUrlValue = "testIIUrl/incorporation-frontend-stubs"
  val cohoUrlValue = "b"

  val mockMetrics = new MockMetrics
  val mockTimer = mockMetrics.mockTimer
  val mockTimerContext = mock[Timer.Context]

  class Setup {
    reset(mockHttp, mockHttpProxy, mockTimerContext, mockIncorporationCheckAPIConnector)

    val connector = new IncorporationAPIConnector {
      override val dateCalculators: DateCalculators = new DateCalculators {}
      val stubBaseUrl = stubUrlValue
      val cohoBaseUrl = cohoUrlValue
      val cohoApiAuthToken = "CohoPublicToken"
      val itemsToFetch = "1"
      val httpNoProxy = mockHttp
      val httpProxy = mockHttpProxy
      val featureSwitch: SCRSFeatureSwitches = new SCRSFeatureSwitches {
        override val KEY_FIRE_SUBS = "w"
        override val KEY_TX_API = "x"
        override val KEY_INCORP_UPDATE = "y"
        override val KEY_SCHED_METRICS = "z"
        override val transactionalAPI = FeatureSwitch(KEY_TX_API, false)
        override val scheduler = FeatureSwitch(KEY_INCORP_UPDATE, false)
        override val KEY_PRO_MONITORING = "p"
      }
      override val metrics: MetricsService = mockMetrics
      override val successCounter: Counter = metrics.transactionApiSuccessCounter
      override val failureCounter: Counter = metrics.transactionApiFailureCounter
      override protected val loggingDays = "MON,TUE,WED,THU,FRI"
      override protected val loggingTimes = "08:00:00_17:00:00"
      override implicit val ec: ExecutionContext = global
    }
  }

  val validSubmissionResponseItems = Seq(
    IncorpUpdate(
      "7894578956784",
      "accepted",
      Some("9999999999"),
      Some(LocalDateTime.of(2016, 8, 10, 0, 0)),
      "123456789")
  )

  "checkForIncorpUpdate" must {

    val testTimepoint = UUID.randomUUID().toString
    val url = s"$testProxyUrl/submissions?items_per_page=1"

    "return a submission status response when no timepoint is provided" in new Setup {
      when(mockHttp.GET[Seq[IncorpUpdate]](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponseItems))

      val result = await(connector.checkForIncorpUpdate())
      result mustBe validSubmissionResponseItems
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$testTimepoint&items_per_page=1"

      when(mockHttp.GET[Seq[IncorpUpdate]](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponseItems))

      val result = await(connector.checkForIncorpUpdate(Some(testTimepoint)))
      result mustBe validSubmissionResponseItems
    }

    "throw IncorpUpdateAPIFailure when Future unexpectedly fails" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))
    }
  }

  "checkForIndividualIncorpUpdate" must {

    val testTimepoint = UUID.randomUUID().toString
    val url = s"$testProxyUrl/submissions?items_per_page=1"

    "return a submission status response when no timepoint is provided" in new Setup {
      when(mockHttp.GET[Seq[IncorpUpdate]](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponseItems))

      val result = await(connector.checkForIndividualIncorpUpdate())
      result mustBe validSubmissionResponseItems
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$testTimepoint&items_per_page=1"

      when(mockHttp.GET[Seq[IncorpUpdate]](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(validSubmissionResponseItems))

      val result = await(connector.checkForIndividualIncorpUpdate(Some(testTimepoint)))
      result mustBe validSubmissionResponseItems
    }

    "report an error when receiving an unexpected error" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIndividualIncorpUpdate(None)))
    }
  }

  "fetchTransactionalData" when {

    val transactionId = "12345"

    "not using the proxy" must {

      val json = Json.parse(
        """
          |{
          | "test":"json"
          |}
        """.stripMargin)

      val fullStubUrl = s"$stubUrlValue/fetch-data/$transactionId"

      "return a SuccessfulTransactionalAPIResponse with a JsValue and stop the timer metric" in new Setup {
        when(mockHttp.GET[TransactionalAPIResponse](eqTo(fullStubUrl), any(), any())(any(), any(), any())).thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(json)))
        when(mockTimer.time()).thenReturn(mockTimerContext)

        val result = await(connector.fetchTransactionalData(transactionId))
        result mustBe SuccessfulTransactionalAPIResponse(json)

        verify(mockTimerContext, times(1)).stop()
      }

      "return a FailedTransactionalAPIResponse" when {

        "any Throwable is caught and the timer metric is stopped" in new Setup {
          when(mockHttp.GET[TransactionalAPIResponse](eqTo(fullStubUrl), any(), any())(any(), any(), any()))
            .thenReturn(Future.failed(new Exception("bang")))
          when(mockTimer.time()).thenReturn(mockTimerContext)

          val result = await(connector.fetchTransactionalData(transactionId))
          result mustBe FailedTransactionalAPIResponse

          verify(mockTimerContext, times(1)).stop()
        }
      }
    }
  }

  "createAPIAuthHeader" must {
    "return a Header with the correct Bearer token" when {
      "a request is made " in new Setup {
        connector.createAPIAuthHeader mustBe Seq("Authorization" -> "Bearer CohoPublicToken")
      }
    }
  }
}