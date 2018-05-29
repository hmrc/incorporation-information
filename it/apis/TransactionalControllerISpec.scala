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

package apis

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching, equalTo}
import helpers.IntegrationSpecBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

class TransactionalControllerISpec extends IntegrationSpecBase {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "metrics.enabled" -> true,
    "microservice.services.public-coho-api.baseUrl" -> s"http://${wiremockHost}:${wiremockPort}",
    "microservice.services.public-coho-api.authToken" -> "SCRSservice",
    "microservice.services.public-coho-api.authTokenNonSCRS" -> "NotAnSCRSservice",
    "microservice.services.scrs-services" -> "VGVzdFNlcnZpY2UsT3RoZXJFeGFtcGxl",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .build

  private def client(path: String) =
    ws.url(s"http://localhost:$port/incorporation-information/$path")
      .withFollowRedirects(false)

  val crn = "CRN-12345"

  "fetchIncorporatedCompanyProfile" should {
    val json = """{"foo":"bar"}"""

    val scrsToken = "U0NSU3NlcnZpY2U="
    val mdtpToken = "Tm90QW5TQ1JTc2VydmljZQ=="

    def stubbedWithToken(token: String) = {
      stubFor(get(urlMatching(s"/company/$crn"))
        .withHeader("Authorization", equalTo(s"Basic $token"))
        .willReturn(
          aResponse().
            withStatus(200).
            withBody(json)
        )
      )
    }
    "return a 200 response with JSON" when {
      "when called by a whitelisted service" in {
        client("test-only/feature-switch/transactionalAPI/on").get().futureValue

        stubbedWithToken(scrsToken)
        val whitelistedResponse = client(s"$crn/incorporated-company-profile").withHeaders("User-Agent" -> "TestService").get().futureValue

        whitelistedResponse.status shouldBe 200
        whitelistedResponse.body shouldBe json
      }
      "when called by an un-whitelisted service" in {
        client("test-only/feature-switch/transactionalAPI/on").get().futureValue

        stubbedWithToken(mdtpToken)
        val whitelistedResponse = client(s"$crn/incorporated-company-profile").withHeaders("User-Agent" -> "Not whitelisted").get().futureValue

        whitelistedResponse.status shouldBe 200
        whitelistedResponse.body shouldBe json
      }
    }
  }

}
