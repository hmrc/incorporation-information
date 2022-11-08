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

package connectors.httpParsers

import Helpers.{LogCapturingHelper, SCRSSpec}
import ch.qos.logback.classic.Level
import connectors.{BaseConnector, FailedTransactionalAPIResponse, IncorpUpdateAPIFailure, SuccessfulTransactionalAPIResponse}
import models.IncorpUpdate
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, NotFoundException, UpstreamErrorResponse}
import utils.{DateCalculators, PagerDutyKeys}

import java.time.LocalDateTime
import java.time.format.DateTimeParseException

class PublicCohoAPIHttpParsersSpec extends SCRSSpec with LogCapturingHelper {

  object PublicCohoAPIHttpParsers extends PublicCohoAPIHttpParsers with BaseConnector {
    override val dateCalculators: DateCalculators = new DateCalculators {}
    override protected val loggingDays = "MON,TUE,WED,THU,FRI,SAT,SUN"
    override protected val loggingTimes = "00:00:00_23:59:59"
  }

  val timepoint = Some("timepoint=now")
  val txId = "tx123456"
  val crn = "1234567890"

  "PublicCohoAPIHttpParsers" when {

    "calling .getCompanyProfileHttpReads(crn: String)" when {

      val rds = PublicCohoAPIHttpParsers.getCompanyProfileHttpReads(crn, isScrs = true)

      s"status is OK ($OK)" must {

        "return the JSON payload" in {

          val json = Json.obj("foo" -> "bar")
          rds.read("", "", HttpResponse(OK, json = json, Map())) mustBe Some(json)
        }
      }

      s"status is NO_CONTENT ($NO_CONTENT)" must {

        "return None" in {

          rds.read("", "", HttpResponse(NO_CONTENT, "")) mustBe None
        }
      }

      s"status is NOT_FOUND ($NOT_FOUND) and `isScrs` is true" must {

        "return None and log a PagerDuty" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.ERROR, s"[PublicCohoAPIHttpParsers] ${PagerDutyKeys.COHO_PUBLIC_API_NOT_FOUND} - Could not find company data for crn: '$crn'")
          }
        }
      }

      s"status is NOT_FOUND ($NOT_FOUND) and `isScrs` is false" must {

        "return None and log an Info message" in {

          val rds = PublicCohoAPIHttpParsers.getCompanyProfileHttpReads(crn, isScrs = false)

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[PublicCohoAPIHttpParsers][getCompanyProfileHttpReads] Could not find company data for crn: '$crn'")
          }
        }
      }

      s"status is SERVICE_UNAVAILABLE ($SERVICE_UNAVAILABLE)" must {

        "return None and log a PagerDuty" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(SERVICE_UNAVAILABLE, "")) mustBe None
            logs.containsMsg(Level.ERROR, s"[PublicCohoAPIHttpParsers] ${PagerDutyKeys.COHO_PUBLIC_API_SERVICE_UNAVAILABLE} - Service unavailable for crn: '$crn'")
          }
        }
      }

      s"status is GATEWAY_TIMEOUT ($GATEWAY_TIMEOUT)" must {

        "return None and log a PagerDuty" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(GATEWAY_TIMEOUT, "")) mustBe None
            logs.containsMsg(Level.ERROR, s"[PublicCohoAPIHttpParsers] ${PagerDutyKeys.COHO_PUBLIC_API_GATEWAY_TIMEOUT} - Gateway timeout for crn: '$crn'")
          }
        }
      }

      s"status is any other 4xx" must {

        "return None and log a PagerDuty" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(BAD_REQUEST, "")) mustBe None
            logs.containsMsg(Level.ERROR, s"[PublicCohoAPIHttpParsers] ${PagerDutyKeys.COHO_PUBLIC_API_4XX} - Returned status code: $BAD_REQUEST for crn: '$crn'")
          }
        }
      }

      s"status is any other 5xx" must {

        "return None and log a PagerDuty" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(NOT_IMPLEMENTED, "")) mustBe None
            logs.containsMsg(Level.ERROR, s"[PublicCohoAPIHttpParsers] ${PagerDutyKeys.COHO_PUBLIC_API_5XX} - Returned status code: $NOT_IMPLEMENTED for crn: '$crn'")
          }
        }
      }

      s"status is any other unexpected status" must {

        "return None and log an error message" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(CREATED, "")) mustBe None
            logs.containsMsg(Level.ERROR, s"[PublicCohoAPIHttpParsers][getCompanyProfileHttpReads] Returned unexpected status code: $CREATED for crn: '$crn'")
          }
        }
      }
    }

    "calling .getOfficerListHttpReads(crn: String)" when {

      val rds = PublicCohoAPIHttpParsers.getOfficerListHttpReads(crn)

      s"status is OK ($OK)" must {

        "return the JSON payload" in {

          val json = Json.obj("foo" -> "bar")
          rds.read("", "", HttpResponse(OK, json = json, Map())) mustBe Some(json)
        }
      }

      s"status is NO_CONTENT ($NO_CONTENT)" must {

        "return None" in {

          rds.read("", "", HttpResponse(NO_CONTENT, "")) mustBe None
        }
      }

      s"status is NOT_FOUND ($NOT_FOUND)" must {

        "return None and log an Info message" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(NOT_FOUND, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[PublicCohoAPIHttpParsers][getOfficerListHttpReads] Could not find officer list for crn: '$crn'")
          }
        }
      }

      s"status is any other unexpected status" must {

        "return None and log an info message" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            rds.read("", "", HttpResponse(CREATED, "")) mustBe None
            logs.containsMsg(Level.INFO, s"[PublicCohoAPIHttpParsers][getOfficerListHttpReads] Returned status code: $CREATED for crn: '$crn'")
          }
        }
      }
    }

    "calling .getOfficerAppointmentHttpReads(officerAppointmentUrl: String)" when {

      val officerAppointmentUrl = "/officer/1"
      val rds = PublicCohoAPIHttpParsers.getOfficerAppointmentHttpReads(officerAppointmentUrl)

      s"status is OK ($OK)" must {

        "return the JSON payload" in {

          val json = Json.obj("foo" -> "bar")
          rds.read("", "", HttpResponse(OK, json = json, Map())) mustBe json
        }
      }

      s"status is NO_CONTENT ($NO_CONTENT)" must {

        "throw a NotFoundException and log an Info message" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            intercept[NotFoundException](rds.read("", "", HttpResponse(NO_CONTENT, "")))
            logs.containsMsg(Level.INFO, s"[getOfficerAppointmentHttpReads] Could not find officer appointment for Officer url - $officerAppointmentUrl")
          }
        }
      }

      s"status is NOT_FOUND ($NOT_FOUND)" must {

        "throw a NotFoundException and log an Info message" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            intercept[NotFoundException](rds.read("", "", HttpResponse(NOT_FOUND, "")))
            logs.containsMsg(Level.INFO, s"[getOfficerAppointmentHttpReads] Could not find officer appointment for Officer url - $officerAppointmentUrl")
          }
        }
      }

      s"status is any other unexpected status" must {

        "throw an UpstreamErrorResponse and log an Info message" in {

          withCaptureOfLoggingFrom(PublicCohoAPIHttpParsers.logger) { logs =>
            intercept[UpstreamErrorResponse](rds.read("", "", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.INFO, s"[getOfficerAppointmentHttpReads] Returned status code: $INTERNAL_SERVER_ERROR for Officer URL: '$officerAppointmentUrl'")
          }
        }
      }
    }
  }
}
