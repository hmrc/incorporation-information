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

import com.github.tomakehurst.wiremock.client.WireMock._
import helpers.IntegrationSpecBase
import play.api.test.FakeApplication

class TransactionalAPIISpec extends IntegrationSpecBase {

  //todo: set feature switch to false
  override implicit lazy val app = FakeApplication(additionalConfiguration = Map(
    "microservice.services.incorp-frontend-stubs.host" -> wiremockHost,
    "microservice.services.incorp-frontend-stubs.port" -> wiremockPort,
    "microservice.services.companies-house.host" -> wiremockHost,
    "microservice.services.companies-house.port" -> wiremockPort
  ))

  "fetchTransactionalData" should {

    val transactionId = "12345"

    val destinationUrl = s"/incorporation-frontend-stubs/fetch-data/$transactionId"

    val body = """{
                |"SCRSCompanyProfile" : {
                |   "test":"json"
                | }
                |}""".stripMargin

    "return a 200 and Json from the companies house API stub" in {
      stubFor(get(urlMatching(destinationUrl))
        .willReturn(
          aResponse().
            withStatus(200).
            withBody(body)
        )
      )

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 200
      response.body shouldBe """{"test":"json"}"""
    }

    "return a 404 if a Json body cannot be returned for the given transaction Id" in {
      stubFor(get(urlMatching(destinationUrl))
        .willReturn(
          aResponse().
            withStatus(404)
        )
      )

      val clientUrl = s"/incorporation-information/$transactionId/company-profile"

      val response = buildClient(clientUrl).get().futureValue
      response.status shouldBe 404
    }
  }
}
