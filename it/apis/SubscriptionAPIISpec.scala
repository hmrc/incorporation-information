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

import helpers.IntegrationSpecBase
import models.Subscription
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.SubscriptionsMongo

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionAPIISpec extends IntegrationSpecBase {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  private def client(path: String) =
    ws.url(s"http://localhost:$port/incorporation-information/$path").
      withFollowRedirects(false)


  class Setup {
    val mongo = new SubscriptionsMongo
    val repository = mongo.store
  }

  override def beforeEach() = new Setup {
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterEach() = new Setup {
    await(repository.drop)
  }

  val transactionId = "123abc"
  val regime = "CT100"
  val subscriber = "abc123"
  val sub = Subscription(transactionId, regime, subscriber)

  "setupSubscription" should {

    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
      stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
      stubGet("/auth/ids", 200, """{"internalId":"Int-xxx","externalId":"Ext-xxx"}""")
    }

    "return a 202 HTTP response" in new Setup {

      setupSimpleAuthMocks()

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post("").futureValue
      response.status shouldBe 202

    }

    "return a 500 HTTP response" in new Setup {
      setupSimpleAuthMocks()

      await(repository.insertSub(sub))

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post("").futureValue
      response.status shouldBe 500
      response.body should include("E11000 duplicate key error")

    }
  }
}

