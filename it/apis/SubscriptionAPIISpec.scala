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

import controllers.SubscriptionController
import itutil.IntegrationSpecBase
import model.Subscription
import mongo.MongoSubscriptionsRepository
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import services.SubscriptionService
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionAPIISpec extends
  IntegrationSpecBase with MongoSpecSupport {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .build

  private def client(path: String) =
    WS.url(s"http://localhost:$port/incorporation-information/$path").
      withFollowRedirects(false)


  class Setup {
    val repository = new MongoSubscriptionsRepository(mongo)
    val subscriptionService = SubscriptionService
    val controller = new SubscriptionController {
      val service = subscriptionService
    }
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  val transactionId = "123abc"
  val regime = "CT100"
  val subscriber = "abc123"
  val sub = Subscription(transactionId, regime, subscriber)

  "setupSubscription" should {

    "return a 202 HTTP response" in new Setup {

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post("").futureValue
      response.status shouldBe 202

    }

    "return a 500 HTTP response" in new Setup {
      repository.insertSub(sub)

      val response = await(client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post("").futureValue)
      response.status shouldBe 500
      response.body should include("E11000 duplicate key error collection:")

    }
  }

}

