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

import java.util.UUID

import Helpers.SCRSSpec
import com.codahale.metrics.{Counter, Timer}
import mocks.MockMetrics
import models.IncorpUpdate
import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import services.MetricsService
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import utils.{DateCalculators, FeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.Future

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
    }

      val timepoint = "old-timepoint"
      val timepointNew = "new-timepoint"
      val timepointSeq = Seq(timepoint,timepointNew)
      val incorpUpdate = Seq(IncorpUpdate("transId", "accepted", None, None, timepoint, None))
      val incorpUpdate2 = IncorpUpdate("transId2", "accepted", None, None, timepoint, None)
      val rejectedIncorpUpdate = IncorpUpdate("7894578956784", "rejected", None, None, "123456789", None)

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
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val manyValidSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"9999999997",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2016-08-10",
      |   "timepoint":"123456789"
      | },
      | {
      |   "company_number":"9999999998",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2016-08-10",
      |   "timepoint":"123456789"
      | },
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
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val manyValidSubmissionResponseJsonWithInvalid = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"9999999997",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2016-08-10",
      |   "timepoint":"123456789"
      | },
      | {
      |   "company_number":"9999999998",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "timepoint":"123456789"
      | },
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
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val validRejectedSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "transaction_status":"rejected",
      |   "transaction_type":"incorporation",
      |   "transaction_id":"7894578956784",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val invalidNoCRNSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2016-08-10",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val invalidEmptyCRNSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2016-08-10",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val invalidDateInvalidSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"12345678",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"20-08-10",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val invalidNoDateSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"12345678",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val invalidEmptyDateSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"12345678",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
      |}
      |}""".stripMargin)

  val invalidDateSubmissionResponseJson = Json.parse(
    """{
      |"items":[
      | {
      |   "company_number":"12345678",
      |   "transaction_status":"accepted",
      |   "transaction_type":"incorporation",
      |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
      |   "transaction_id":"7894578956784",
      |   "incorporated_on":"2002-20-00",
      |   "timepoint":"123456789"
      | }
      |],
      |"links":{
      | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
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
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validSubmissionResponseJson))))

      val result = await(connector.checkForIncorpUpdate())
      result shouldBe validSubmissionResponseItems

      urlCaptor.getValue shouldBe url
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$testTimepoint&items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validSubmissionResponseJson))))

      val result = await(connector.checkForIncorpUpdate(Some(testTimepoint)))
      result shouldBe validSubmissionResponseItems

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 400" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream4xx" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream4xxResponse("429", 429, 429)))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an Upstream5xx" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(Upstream5xxResponse("502", 502, 502)))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an unexpected error" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }
  }

  "checkForIndividualIncorpUpdate" should {

    val testTimepoint = UUID.randomUUID().toString

    "return a submission status response when no timepoint is provided" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validSubmissionResponseJson))))

      val result = await(connector.checkForIndividualIncorpUpdate())
      result shouldBe validSubmissionResponseItems

      urlCaptor.getValue shouldBe url
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$testTimepoint&items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[HttpResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validSubmissionResponseJson))))

      val result = await(connector.checkForIndividualIncorpUpdate(Some(testTimepoint)))
      result shouldBe validSubmissionResponseItems

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving a 404" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIndividualIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

    "report an error when receiving an unexpected error" in new Setup {
      val url = s"$testProxyUrl/submissions?items_per_page=1"

      val urlCaptor = ArgumentCaptor.forClass(classOf[String])

      when(mockHttp.GET[IncorpUpdatesResponse](urlCaptor.capture())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIndividualIncorpUpdate(None)))

      urlCaptor.getValue shouldBe url
    }

  }

  "checkForIncorpUpdate" should {
    "return a sequence of updates" when {
      "it has no invalid updates" in new Setup {
        def incorpUpdate(crn: String) = IncorpUpdate(
          "7894578956784",
          "accepted",
          Some(crn),
          Some(DateTime.parse("2016-08-10")),
          "123456789"
        )

        when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(manyValidSubmissionResponseJson))))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
        result shouldBe Seq(incorpUpdate("9999999997"), incorpUpdate("9999999998"), incorpUpdate("9999999999"))
      }
    }
    "return no updates for an accepted response from CH" when {
      "there is no CRN" in new Setup {
        when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(invalidNoCRNSubmissionResponseJson))))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
        result shouldBe Seq()
      }
      "there is no date of incorporation" in new Setup {
        when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(invalidNoDateSubmissionResponseJson))))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
        result shouldBe Seq()
      }
      "there is a date of incorporation in the wrong format" in new Setup {
        when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(invalidDateSubmissionResponseJson))))

        val result = connector.checkForIncorpUpdate(Some(timepoint))
        val failure = intercept[IncorpUpdateAPIFailure](await(result))

        failure.ex shouldBe a[IllegalArgumentException]
      }
        "there is an empty date of incorporation" in new Setup {
          when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
            .thenReturn(Future.successful(HttpResponse(200, Some(invalidEmptyDateSubmissionResponseJson))))

          val result = connector.checkForIncorpUpdate(Some(timepoint))
          val failure = intercept[IncorpUpdateAPIFailure]( await(result) )

          failure.ex shouldBe a[IllegalArgumentException]
      }
      "only one of many submissions is invalid" in new Setup {
        when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, Some(manyValidSubmissionResponseJsonWithInvalid))))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))

        result shouldBe Seq()
      }
    }

    "return updates when there is any number of rejected CH responses" in new Setup {
      when(mockHttp.GET[HttpResponse](any())(Matchers.any(), Matchers.any(), Matchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, Some(validRejectedSubmissionResponseJson))))

      val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
      result shouldBe Seq(rejectedIncorpUpdate)
    }

  }

  "fetchTransactionalData" when {

    val transactionId = "12345"

    "not using the proxy" should {

      val json = Json.parse(
        """
          |{
          | "test":"json"
          |}
        """.stripMargin)

      val fullStubUrl = s"$stubUrlValue/fetch-data/$transactionId"

      "return a SuccessfulTransactionalAPIResponse with a JsValue and stop the timer metric" in new Setup {
        when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any(), any())).thenReturn(Future.successful(json))
        when(mockTimer.time()).thenReturn(mockTimerContext)

        val result = await(connector.fetchTransactionalData(transactionId))
        result shouldBe SuccessfulTransactionalAPIResponse(json)

        verify(mockTimerContext, times(1)).stop()
      }

      "return a FailedTransactionalAPIResponse" when {

        "a 404 NotFound status code is returned" in new Setup {
          val response = Future.failed(new NotFoundException(""))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }

        "any other http exception status (4xx / 5xx) is returned" in new Setup {
          val response = Future.failed(new HttpException("Any http status exception that extends this class is either 4xx or 5xx", 9999))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }

        "any Throwable is caught and the timer metric is stopped" in new Setup {
          when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any(), any()))
            .thenReturn(Future.failed(new Throwable("")))
          when(mockTimer.time()).thenReturn(mockTimerContext)

          val result = await(connector.fetchTransactionalData(transactionId))
          result shouldBe FailedTransactionalAPIResponse

          verify(mockTimerContext, times(1)).stop()
        }
      }
    }
  }
  "createAPIAuthHeader" should {
    "return a HeaderCarrier with the correct Bearer token" when {
      "a request is made " in new Setup {
        connector.createAPIAuthHeader.authorization shouldBe Some(Authorization("Bearer CohoPublicToken"))
      }
    }
  }
}