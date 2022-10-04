/*
 * Copyright 2022 HM Revenue & Customs
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
import models.IncorpUpdate
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

    val timepoint = "old-timepoint"
    val timepointNew = "new-timepoint"
    val timepointSeq = Seq(timepoint, timepointNew)
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
      Some(LocalDateTime.of(2016, 8, 10, 0, 0)),
      "123456789")
  )

  "checkForIncorpUpdate" must {

    val testTimepoint = UUID.randomUUID().toString
    val url = s"$testProxyUrl/submissions?items_per_page=1"

    "return a submission status response when no timepoint is provided" in new Setup {
      when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, json = validSubmissionResponseJson, Map())))

      val result = await(connector.checkForIncorpUpdate())
      result mustBe validSubmissionResponseItems
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$testTimepoint&items_per_page=1"

      when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, json = validSubmissionResponseJson, Map())))

      val result = await(connector.checkForIncorpUpdate(Some(testTimepoint)))
      result mustBe validSubmissionResponseItems
    }

    "report an error when receiving a 400" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new BadRequestException("400")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))
    }

    "report an error when receiving a 404" in new Setup {

      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))
    }

    "report an error when receiving an Upstream4xx" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("429", 429, 429)))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))
    }

    "report an error when receiving an Upstream5xx" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("502", 502, 502)))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))
    }

    "report an error when receiving an unexpected error" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIncorpUpdate(None)))
    }
  }

  "checkForIndividualIncorpUpdate" must {

    val testTimepoint = UUID.randomUUID().toString
    val url = s"$testProxyUrl/submissions?items_per_page=1"

    "return a submission status response when no timepoint is provided" in new Setup {
      when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, json = validSubmissionResponseJson, Map())))

      val result = await(connector.checkForIndividualIncorpUpdate())
      result mustBe validSubmissionResponseItems
    }

    "verify a timepoint is appended as a query string to the url when one is supplied" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$testTimepoint&items_per_page=1"

      when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, json = validSubmissionResponseJson, Map())))

      val result = await(connector.checkForIndividualIncorpUpdate(Some(testTimepoint)))
      result mustBe validSubmissionResponseItems
    }

    "report an error when receiving a 404" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NotFoundException("404")))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIndividualIncorpUpdate(None)))
    }

    "report an error when receiving an unexpected error" in new Setup {
      when(mockHttp.GET[IncorpUpdatesResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.failed(new NoSuchElementException))

      intercept[IncorpUpdateAPIFailure](await(connector.checkForIndividualIncorpUpdate(None)))
    }

  }

  "checkForIncorpUpdate" must {
    "return a sequence of updates" when {
      "it has no invalid updates" in new Setup {
        val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"

        def incorpUpdate(crn: String) = IncorpUpdate(
          "7894578956784",
          "accepted",
          Some(crn),
          Some(LocalDate.parse("2016-08-10").atStartOfDay()),
          "123456789"
        )

        when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = manyValidSubmissionResponseJson, Map())))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
        result mustBe Seq(incorpUpdate("9999999997"), incorpUpdate("9999999998"), incorpUpdate("9999999999"))
      }
    }
    "return no updates for an accepted response from CH" when {
      "there is no CRN" in new Setup {
        val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"
        when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = invalidNoCRNSubmissionResponseJson, Map())))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
        result mustBe Seq()
      }
      "there is no date of incorporation" in new Setup {
        val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"

        when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = invalidNoDateSubmissionResponseJson, Map())))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
        result mustBe Seq()
      }
      "there is a date of incorporation in the wrong format" in new Setup {
        val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"

        when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = invalidDateSubmissionResponseJson, Map())))

        val result = connector.checkForIncorpUpdate(Some(timepoint))
        val failure = intercept[IncorpUpdateAPIFailure](await(result))

        failure.ex mustBe a[DateTimeParseException]
      }
      "there is an empty date of incorporation" in new Setup {
        val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"

        when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = invalidEmptyDateSubmissionResponseJson, Map())))

        val result = connector.checkForIncorpUpdate(Some(timepoint))
        val failure = intercept[IncorpUpdateAPIFailure](await(result))

        failure.ex mustBe a[DateTimeParseException]
      }
      "only one of many submissions is invalid" in new Setup {
        val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"

        when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenReturn(Future.successful(HttpResponse(200, json = manyValidSubmissionResponseJsonWithInvalid, Map())))

        val result = await(connector.checkForIncorpUpdate(Some(timepoint)))

        result mustBe Seq()
      }
    }

    "return updates when there is any number of rejected CH responses" in new Setup {
      val url = s"$testProxyUrl/submissions?timepoint=$timepoint&items_per_page=1"

      when(mockHttp.GET[HttpResponse](eqTo(url), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(200, json = validRejectedSubmissionResponseJson, Map())))

      val result = await(connector.checkForIncorpUpdate(Some(timepoint)))
      result mustBe Seq(rejectedIncorpUpdate)
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
        when(mockHttp.GET[JsValue](eqTo(fullStubUrl), any(), any())(any(), any(), any())).thenReturn(Future.successful(json))
        when(mockTimer.time()).thenReturn(mockTimerContext)

        val result = await(connector.fetchTransactionalData(transactionId))
        result mustBe SuccessfulTransactionalAPIResponse(json)

        verify(mockTimerContext, times(1)).stop()
      }

      "return a FailedTransactionalAPIResponse" when {

        "a 404 NotFound status code is returned" in new Setup {
          val response = Future.failed(new NotFoundException(""))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl), any(), any())(any(), any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result mustBe FailedTransactionalAPIResponse
        }

        "any other http exception status (4xx / 5xx) is returned" in new Setup {
          val response = Future.failed(new HttpException("Any http status exception that extends this class is either 4xx or 5xx", 9999))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl), any(), any())(any(), any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result mustBe FailedTransactionalAPIResponse
        }

        "any Throwable is caught and the timer metric is stopped" in new Setup {
          when(mockHttp.GET[JsValue](eqTo(fullStubUrl), any(), any())(any(), any(), any()))
            .thenReturn(Future.failed(new Throwable("")))
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