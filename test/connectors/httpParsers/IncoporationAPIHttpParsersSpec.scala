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

package connectors.httpParsers

import Helpers.{LogCapturingHelper, SCRSSpec}
import ch.qos.logback.classic.Level
import connectors.{BaseConnector, FailedTransactionalAPIResponse, IncorpUpdateAPIFailure, SuccessfulTransactionalAPIResponse}
import models.IncorpUpdate
import play.api.http.Status.{BAD_REQUEST, GATEWAY_TIMEOUT, INTERNAL_SERVER_ERROR, NOT_FOUND, NOT_IMPLEMENTED, NO_CONTENT, OK, SERVICE_UNAVAILABLE}
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import utils.{DateCalculators, PagerDutyKeys}

import java.time.LocalDateTime
import java.time.format.DateTimeParseException

class IncoporationAPIHttpParsersSpec extends SCRSSpec with LogCapturingHelper {

  object IncorporationAPIHttpParsers extends IncorporationAPIHttpParsers with BaseConnector {
    override val dateCalculators: DateCalculators = new DateCalculators {}
    override protected val loggingDays = "MON,TUE,WED,THU,FRI,SAT,SUN"
    override protected val loggingTimes = "00:00:00_23:59:59"
  }

  val timepoint = Some("timepoint=now")
  val txId = "tx123456"

  "IncorporationAPIHttpParsers" when {

    "calling .checkForIncorpUpdateHttpReads(timepoint: Option[String])" must {

      val rds = IncorporationAPIHttpParsers.checkForIncorpUpdateHttpReads(timepoint)

      "return a sequence of incorp updates" when {

        "status is OK (200) and JSON is valid" in {

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

          val validSubmissionResponseItems = Seq(
            IncorpUpdate(
              "7894578956784",
              "accepted",
              Some("9999999999"),
              Some(LocalDateTime.of(2016, 8, 10, 0, 0)),
              "123456789")
          )

          val response = HttpResponse(OK, json = validSubmissionResponseJson, Map())
          rds.read("", "", response) mustBe validSubmissionResponseItems
        }

        "status is OK (200) and JSON is valid (including rejected submission)" in {

          val rejectedIncorpUpdate = IncorpUpdate("7894578956784", "rejected", None, None, "123456789", None)

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

          val response = HttpResponse(OK, json = validRejectedSubmissionResponseJson, Map())
          rds.read("", "", response) mustBe Seq(rejectedIncorpUpdate)
        }
      }

      "return empty sequence" when {

        "status is OK (200) and JSON is invalid" in {

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

          val response = HttpResponse(OK, json = manyValidSubmissionResponseJsonWithInvalid, Map())
          rds.read("", "", response) mustBe Seq()
        }

        "status is OK (200) and CRN is missing" in {

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

          val response = HttpResponse(OK, json = invalidNoCRNSubmissionResponseJson, Map())
          rds.read("", "", response) mustBe Seq()
        }

        "status is OK (200) and Date is missing" in {

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

          val response = HttpResponse(OK, json = invalidNoDateSubmissionResponseJson, Map())
          rds.read("", "", response) mustBe Seq()
        }

        "status is NO_CONTENT (204)" in {

          rds.read("", "", HttpResponse(NO_CONTENT, "")) mustBe Seq()
        }
      }

      "throw DateInvalidException" when {

        "status is OK (200) and Date is in wrong format" in {

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

          val response = HttpResponse(OK, json = invalidDateSubmissionResponseJson, Map())
          intercept[DateTimeParseException](rds.read("", "", response))
        }
      }

      "return a IncorpUpdateAPIFailure containing an UpstreamErrorResponse for any other status (logging error)" in {

        val errMsg = s"[checkForIncorpUpdateHttpReads] request to fetch incorp updates returned a $INTERNAL_SERVER_ERROR. No incorporations were processed for timepoint $timepoint"

        withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
          val ex = intercept[IncorpUpdateAPIFailure](rds.read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
          ex mustBe IncorpUpdateAPIFailure(UpstreamErrorResponse(errMsg, INTERNAL_SERVER_ERROR))
          logs.containsMsg(Level.ERROR, errMsg)
        }
      }
    }

    "calling .checkForIndividualIncorpUpdateHttpReads(timepoint: Option[String])" must {

      val rds = IncorporationAPIHttpParsers.checkForIndividualIncorpUpdateHttpReads(timepoint)

      "return a sequence of incorp updates" when {

        "status is OK (200) and JSON is valid" in {

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

          val validSubmissionResponseItems = Seq(
            IncorpUpdate(
              "7894578956784",
              "accepted",
              Some("9999999999"),
              Some(LocalDateTime.of(2016, 8, 10, 0, 0)),
              "123456789")
          )

          val response = HttpResponse(OK, json = validSubmissionResponseJson, Map())
          rds.read("", "", response) mustBe validSubmissionResponseItems
        }

        "status is OK (200) and JSON is valid (including rejected submission)" in {

          val rejectedIncorpUpdate = IncorpUpdate("7894578956784", "rejected", None, None, "123456789", None)

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

          val response = HttpResponse(OK, json = validRejectedSubmissionResponseJson, Map())
          rds.read("", "", response) mustBe Seq(rejectedIncorpUpdate)
        }
      }

      "return empty sequence" when {

        "status is NO_CONTENT (204)" in {

          rds.read("", "", HttpResponse(NO_CONTENT, "")) mustBe Seq()
        }
      }

      "throw DateInvalidException" when {

        "status is OK (200) and Date is in wrong format" in {

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

          val response = HttpResponse(OK, json = invalidDateSubmissionResponseJson, Map())
          intercept[DateTimeParseException](rds.read("", "", response))
        }
      }

      "return a IncorpUpdateAPIFailure containing an UpstreamErrorResponse for any other status (logging error)" in {

        val errMsg = s"[checkForIndividualIncorpUpdateHttpReads] request to fetch incorp updates returned a $INTERNAL_SERVER_ERROR. No incorporations were processed for timepoint $timepoint"

        withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
          val ex = intercept[IncorpUpdateAPIFailure](rds.read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
          ex mustBe IncorpUpdateAPIFailure(UpstreamErrorResponse(errMsg, INTERNAL_SERVER_ERROR))
          logs.containsMsg(Level.ERROR, errMsg)
        }
      }
    }

    "calling .fetchTransactionalDataHttpReads(txId: String)" must {

      val rds = IncorporationAPIHttpParsers.fetchTransactionalDataHttpReads(txId)

      "return SuccessfulTransactionalAPIResponse with the response Json" when {

        "status is 2xx" in {

          val dummyJson = Json.obj("foo" -> "bar")

          rds.read("", "", HttpResponse(OK, json = dummyJson, Map())) mustBe SuccessfulTransactionalAPIResponse(dummyJson)
        }
      }

      "return FailedTransactionalAPIResponse" when {

        s"status is NOT_FOUND ($NOT_FOUND) - and raise a PagerDuty" in {

          withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(NOT_FOUND, "")) mustBe FailedTransactionalAPIResponse
            logs.containsMsg(Level.ERROR, s"[IncorporationAPIHttpParsers] ${PagerDutyKeys.COHO_TX_API_NOT_FOUND} - Could not find incorporation data for txId: '$txId'")
          }
        }

        "status is any other 4xx - and raise a PagerDuty" in {

          withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(BAD_REQUEST, "")) mustBe FailedTransactionalAPIResponse
            logs.containsMsg(Level.ERROR, s"[IncorporationAPIHttpParsers] ${PagerDutyKeys.COHO_TX_API_4XX} - $BAD_REQUEST returned for txId: '$txId'")
          }
        }

        s"status is GATEWAY_TIMEOUT ($GATEWAY_TIMEOUT) - and raise a PagerDuty" in {

          withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(GATEWAY_TIMEOUT, "")) mustBe FailedTransactionalAPIResponse
            logs.containsMsg(Level.ERROR, s"[IncorporationAPIHttpParsers] ${PagerDutyKeys.COHO_TX_API_GATEWAY_TIMEOUT} - Gateway timeout for txId: '$txId'")
          }
        }

        s"status is SERVICE_UNAVAILABLE ($SERVICE_UNAVAILABLE) - and raise a PagerDuty" in {

          withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(SERVICE_UNAVAILABLE, "")) mustBe FailedTransactionalAPIResponse
            logs.containsMsg(Level.ERROR, s"[IncorporationAPIHttpParsers] ${PagerDutyKeys.COHO_TX_API_SERVICE_UNAVAILABLE} - Service unavailable for txId: '$txId'")
          }
        }

        s"status is any other 5xx response - and raise a PagerDuty" in {

          withCaptureOfLoggingFrom(IncorporationAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(NOT_IMPLEMENTED, "")) mustBe FailedTransactionalAPIResponse
            logs.containsMsg(Level.ERROR, s"[IncorporationAPIHttpParsers] ${PagerDutyKeys.COHO_TX_API_5XX} - Returned status code: $NOT_IMPLEMENTED for txId: '$txId'")
          }
        }
      }
    }
  }
}
