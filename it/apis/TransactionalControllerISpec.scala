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

import com.github.tomakehurst.wiremock.client.WireMock._
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

  "fetchSicCodes" should {
    val txid = "transid"

    "return no sic codes while using public API with a CRN if the API is down" in {
      stubFor(get(urlMatching(s"/company/$crn"))
        .willReturn(
          aResponse()
            .withStatus(502)
        )
      )
      val result = client(s"sic-codes/crn/$crn").get().futureValue
      result.status shouldBe 204
    }

    "return sic codes while using public API with a CRN" in {
      stubFor(get(urlMatching(s"/company/$crn"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"foo":"bar", "sic_codes":["12345", "54321"], "wibble":"pop"}""")
        )
      )
      val result = client(s"sic-codes/crn/$crn").get().futureValue
      result.status shouldBe 200
      result.body shouldBe """{"sic_codes":["12345","54321"]}"""
    }
    "return no sic codes while using public API with a CRN" in {
      stubFor(get(urlMatching(s"/company/$crn"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"foo":"bar", "wibble":"pop"}""")
        )
      )
      val result = client(s"sic-codes/crn/$crn").get().futureValue
      result.status shouldBe 204
    }

    "return sic codes while using transactional API with a transaction ID" in {
      stubFor(get(urlMatching(s"/submissionData/$txid"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                |  "foo":"bar",
                |  "sic_codes":[
                |    {
                |      "sic_description":"wibble",
                |      "sic_code":"12345"
                |    },
                |    {
                |      "sic_description":"pop",
                |      "sic_code":"54321"
                |    }
                |  ],
                |  "wibble":"pop"
                |}
                |""".stripMargin)
        )
      )
      val result = client(s"sic-codes/transaction/$txid").get().futureValue
      result.status shouldBe 200
      result.body shouldBe
        """{"sic_codes":["12345","54321"]}""".stripMargin
    }

    "return no sic codes if JSON cannot be parsed" in {
      stubFor(get(urlMatching(s"/submissionData/$txid"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                |  "foo":"bar",
                |  "sic_codes"{
                |    { . _ . }
                |      "sic_description":"wibble",
                |      "sic_code":"12345"
                |    },///{""]}}
                |    {
                |      "sic_description":"pop",
                |      "sic_code":"54321"
                |    }
                |  ],
                |  "wibble":"pop"
                |}
                |""".stripMargin)
        )
      )
      val result = client(s"sic-codes/transaction/$txid").get().futureValue
      result.status shouldBe 204
    }
    "return no sic codes while using transactional API with a transaction ID" in {
      stubFor(get(urlMatching(s"/submissionData/$txid"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"foo":"bar", "sic_codes":["12345", "54321"], "wibble":"pop"}""")
        )
      )
      val result = client(s"sic-codes/transaction/$txid").get().futureValue
      result.status shouldBe 200
      result.body shouldBe """{"sic_codes":[]}"""
    }
    "return no sic codes while using transactional API with a transaction ID when the API is down" in {
      stubFor(get(urlMatching(s"/submissionData/$txid"))
        .willReturn(
          aResponse()
            .withStatus(502)
            .withBody("""{"foo":"bar", "sic_codes":["12345", "54321"], "wibble":"pop"}""")
        )
      )
      val result = client(s"sic-codes/transaction/$txid").get().futureValue
      result.status shouldBe 204
    }
  }

}
