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

import com.github.tomakehurst.wiremock.client.WireMock.{getRequestedFor, matching, urlMatching, verify}
import helpers.IntegrationSpecBase
import models.IncorpUpdate
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.http.{Authorization, BadRequestException, HeaderCarrier}

import java.time.LocalDateTime

class IncorpUpdateConnectorISpec extends IntegrationSpecBase {

  val additionalConfiguration: Map[String, Any] = Map(
    "metrics.enabled" -> true,
    "microservice.services.incorp-update-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/incorporation-frontend-stubs",
    "microservice.services.incorp-update-api.url" -> s"http://${wiremockHost}:${wiremockPort}/coho",
    "microservice.services.incorp-update-api.token" -> "N/A"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .build


  "fetchTransactionalData" must {
    val transactionId = "12345"
    val destinationUrl = s"/incorporation-frontend-stubs/fetch-data/$transactionId"

    val responseOne =
      s"""{
         |"items":[
         | {
         |   "company_number":"9999999999",
         |   "transaction_status":"accepted",
         |   "transaction_type":"incorporation",
         |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
         |   "transaction_id":"$transactionId",
         |   "incorporated_on":"2016-08-10",
         |   "timepoint":"123456789"
         | }
         |],
         |"links":{
         | "next":"https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
         |}
         |}""".stripMargin


    val date = LocalDateTime.of(2016, 8, 10, 0, 0)
    val item1 = IncorpUpdate(transactionId, "accepted", Some("9999999999"), Some(date), "123456789", None)

    "items from the sample response keeping headers when getting from stub" in {
      setupFeatures(transactionalAPI = false)
      stubGet("/incorporation-frontend-stubs/submissions.*", 200, responseOne)
      val connector = app.injector.instanceOf[IncorporationAPIConnector]

      val result = await(connector.checkForIncorpUpdate(None)(HeaderCarrier(authorization = Some(Authorization("foo")), extraHeaders = Seq("bar" -> "wizz"))))
      result mustBe Seq(item1)
      verify(getRequestedFor(urlMatching("/incorporation-frontend-stubs/submissions.*"))
        .withHeader("Authorization", matching("foo"))
        .withHeader("bar", matching("wizz")))
    }

    "Return no items if a 204 (no content) result is returned" in {
      setupFeatures(transactionalAPI = false)
      stubGet("/incorporation-frontend-stubs/submissions.*", 204, "")

      val connector = app.injector.instanceOf[IncorporationAPIConnector]

      val f = connector.checkForIncorpUpdate(None)(HeaderCarrier())
      val r = await(f)
      r mustBe Seq()
    }

    "Return no items if a 400 (bad request) result is returned" in {
      setupFeatures(transactionalAPI = false)
      stubGet("/incorporation-frontend-stubs/submissions.*", 400, "")
      val connector = app.injector.instanceOf[IncorporationAPIConnector]
      val f = connector.checkForIncorpUpdate(None)(HeaderCarrier())
      val failure = intercept[IncorpUpdateAPIFailure](await(f))
      failure.ex mustBe a[BadRequestException]
    }

  }
}