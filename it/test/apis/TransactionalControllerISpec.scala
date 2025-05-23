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

package test.apis

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, Json}
import test.helpers.IntegrationSpecBase

class TransactionalControllerISpec extends IntegrationSpecBase {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration: Map[String, Any] = Map(
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

  "fetchIncorporatedCompanyProfile" must {
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
      "when called by an allowlisted service" in {
        client("test-only/feature-switch/transactionalAPI/on").get().futureValue

        stubbedWithToken(scrsToken)
        val allowlistedResponse = client(s"$crn/incorporated-company-profile").withHttpHeaders("User-Agent" -> "TestService").get().futureValue

        allowlistedResponse.status mustBe 200
        allowlistedResponse.body mustBe json
      }
      "when called by an un-allowlisted service" in {
        client("test-only/feature-switch/transactionalAPI/on").get().futureValue

        stubbedWithToken(mdtpToken)
        val allowlistedResponse = client(s"$crn/incorporated-company-profile").withHttpHeaders("User-Agent" -> "Not allowlisted").get().futureValue

        allowlistedResponse.status mustBe 200
        allowlistedResponse.body mustBe json
      }
    }
  }

  "fetchSicCodes" must {
    val txid = "transid"

    "return no sic codes while using public API with a CRN if the API is down" in {
      stubFor(get(urlMatching(s"/company/$crn"))
        .willReturn(
          aResponse()
            .withStatus(502)
        )
      )
      val result = client(s"sic-codes/crn/$crn").get().futureValue
      result.status mustBe 204
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
      result.status mustBe 200
      result.body mustBe """{"sic_codes":["12345","54321"]}"""
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
      result.status mustBe 204
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
      result.status mustBe 200
      result.body mustBe
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
      result.status mustBe 204
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
      result.status mustBe 200
      result.body mustBe """{"sic_codes":[]}"""
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
      result.status mustBe 204
    }
  }
  "fetchShareholders" must {
    val txid = "transid"

    "return 200 with a list of shareholders" in {
      stubFor(get(urlMatching(s"/submissionData/$txid"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              """{
                |"foo":"bar",
                |"shareholders": [
                |  {
                |  "subscriber_type": "corporate",
                |    "name": "big company",
                |    "address": {
                |    "premises": "11",
                |    "address_line_1": "Drury Lane",
                |    "address_line_2": "West End",
                |    "locality": "London",
                |    "country": "United Kingdom",
                |    "postal_code": "SW2 B2B"
                |    },
                |  "percentage_voting_rights": 75.34
                |  },
                |   {
                |  "subscriber_type": "corporate",
                |    "name": "big company 1",
                |    "address": {
                |    "premises": "11",
                |    "address_line_1": "Drury Lane 1",
                |    "address_line_2": "West End 1",
                |    "locality": "London 1",
                |    "country": "United Kingdom 1",
                |    "postal_code": "SW2 B2B 1"
                |    },
                |  "percentage_voting_rights": 20.34
                |  }
                |  ]
                | }
              """.stripMargin)))

      val result = client(s"shareholders/$txid").get().futureValue
      result.status mustBe 200
      result.json.as[JsArray] mustBe Json.parse(
        """
          |[
          |  {
          |  "subscriber_type": "corporate",
          |    "name": "big company",
          |    "address": {
          |    "premises": "11",
          |    "address_line_1": "Drury Lane",
          |    "address_line_2": "West End",
          |    "locality": "London",
          |    "country": "United Kingdom",
          |    "postal_code": "SW2 B2B"
          |    },
          |  "percentage_voting_rights": 75.34
          |  },
          |   {
          |  "subscriber_type": "corporate",
          |    "name": "big company 1",
          |    "address": {
          |    "premises": "11",
          |    "address_line_1": "Drury Lane 1",
          |    "address_line_2": "West End 1",
          |    "locality": "London 1",
          |    "country": "United Kingdom 1",
          |    "postal_code": "SW2 B2B 1"
          |    },
          |  "percentage_voting_rights": 20.34
          |  }
          |  ]
        """.stripMargin).as[JsArray]
    }
  }
}