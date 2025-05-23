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

package test.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import connectors.FiringSubscriptionsConnector
import models.{IncorpUpdate, IncorpUpdateResponse}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import test.helpers.IntegrationSpecBase
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

class FiringSubscriptionsConnectorISpec extends IntegrationSpecBase {

  val mockHost: String = wiremockHost
  val mockPort: Int = wiremockPort
  val mockUrl = s"http://$mockHost:$mockPort"

  val additionalConfiguration = Map(
    "microservice.services.fire-subs-api.host" -> s"$mockHost",
    "microservice.services.fire-subs-api.port" -> s"$mockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .build

  implicit val hc = HeaderCarrier()

  val incorpUpdate: IncorpUpdate = IncorpUpdate("trans123", "rejected", None, None, "tp", Some("description"))
  val incorpUpdateResponse: IncorpUpdateResponse = IncorpUpdateResponse("CT", "test", "www.test.com", incorpUpdate)


  "fireIncorpUpdate" must {

    "return a 200 HTTP response from a given callbackUrl" in {

      val firingSubscriptionsConnector = new FiringSubscriptionsConnector(app.injector.instanceOf[HttpClient])

      def connectToAnyURL = firingSubscriptionsConnector.connectToAnyURL(incorpUpdateResponse, s"$mockUrl/testuri")

      stubFor(post(urlMatching("/testuri"))
        .willReturn(
          aResponse()
            .withStatus(200)
        )
      )
      await(connectToAnyURL).status mustBe 200
    }
  }
}