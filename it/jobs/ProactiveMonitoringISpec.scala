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

package jobs

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import helpers.{FakeAppConfig, IntegrationSpecBase}
import org.scalatest.concurrent.Eventually
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.{Application, Logger}
import services.ProactiveMonitoringService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global


class ProactiveMonitoringISpec extends IntegrationSpecBase with FakeAppConfig
  with Eventually {

  val txId = "test-txid"
  val crn = "test-crn"
  val additionalConfiguration = Map(
    "schedules.fire-subs-job.enabled" -> "false",
    "schedules.incorp-update-job.enabled" -> "false",
    "schedules.proactive-monitoring-job.enabled" -> "false",
    "schedules.metrics-job.enabled" -> "false"
  )
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .build

  val service: ProactiveMonitoringService = app.injector.instanceOf[ProactiveMonitoringService]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def stubFetchTransactionalAPI(status: Int): StubMapping = {
    stubGet(s"/submissionData/$txId", status, Json.obj("test" -> "json").toString())
  }

  def stubFetchCompanyProfilePublicAPI(status: Int): StubMapping = {
    stubGet(s"/company/$crn", status, Json.obj("test" -> "json").toString())
  }

  "pollAPIs" should {

    "poll both the transaction API and public API and both return success" in {
      setupFeatures(transactionalAPI = true)

      stubFetchTransactionalAPI(200)
      stubFetchCompanyProfilePublicAPI(200)

      val (txApiResponse, publicApiResponse) = await(service.pollAPIs)
      txApiResponse shouldBe "polling transactional API - success"
      publicApiResponse shouldBe "polling public API - success"
    }

    "poll both the transaction API and public API, return failures and alert log on 5xx" in {
      setupFeatures(transactionalAPI = true)

      stubFetchTransactionalAPI(502)
      stubFetchCompanyProfilePublicAPI(502)

      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        val (txApiResponse, publicApiResponse) = await(service.pollAPIs)
        txApiResponse shouldBe "polling transactional API - failed"
        publicApiResponse shouldBe "polling public API - failed"

        eventually {
          val logMessages = loggingEvents.map(_.getMessage)
          logMessages.toString().contains("COHO_TX_API_5XX") shouldBe true
          logMessages.toString().contains("COHO_PUBLIC_API_5XX") shouldBe true
        }
      }
    }
  }
}