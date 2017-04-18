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
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo}
import play.api.libs.json.{JsObject, Json, __}

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

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]


  class Setup {
    val incRepo = new IncorpUpdateMongo(reactiveMongoComponent).repo
    val repository = new SubscriptionsMongo(reactiveMongoComponent).repo
    val queueRepo = new QueueMongo(reactiveMongoComponent).repo

    def insert(sub: Subscription) = await(repository.insert(sub))
    def insert(update: IncorpUpdate) = await(incRepo.insert(update))
    def insert(queuedIncorpUpdate: QueuedIncorpUpdate) = await(queueRepo.insert(queuedIncorpUpdate))
    def subCount = await(repository.count)
    def incCount = await(incRepo.count)
    def queueCount = await(queueRepo.count)
  }

  override def beforeEach() = new Setup {
    await(repository.drop)
    await(repository.ensureIndexes)
    await(incRepo.drop)
    await(incRepo.ensureIndexes)
    await(queueRepo.drop)
    await(queueRepo.ensureIndexes)
  }

  override def afterEach() = new Setup {
    await(repository.drop)
    await(incRepo.drop)
  }

  def extractTimestamp(json: JsObject): (Long, JsObject) = {
    val generatedTS = (json \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "timestamp").as[Long]
    val t = (__ \ "SCRSIncorpStatus" \ "IncorpStatusEvent" \ "timestamp").json.prune
    (generatedTS, (json transform t).get)
  }

  val transactionId = "123abc"
  val regime = "CT100"
  val subscriber = "abc123"
  val url = "www.test.com"
  val sub = Subscription(transactionId, regime, subscriber, url)
  val incorpUpdate = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))

  val json = Json.parse(
    """
      |{
      |  "SCRSIncorpSubscription": {
      |    "callbackUrl": "www.test.com"
      |  }
      |}
    """.stripMargin
  )

  val jsonUpdate = Json.parse(
    """
      |{
      |  "SCRSIncorpSubscription": {
      |    "callbackUrl": "www.testUpdate.com"
      |  }
      |}
    """.stripMargin
  )

  val jsonIncorpUpdate = Json.parse(
    """
      |{
      |  "SCRSIncorpStatus":{
      |    "IncorpSubscriptionKey":{
      |      "subscriber":"abc123",
      |      "discriminator":"CT100",
      |      "transactionId":"123abc"
      |    },
      |    "SCRSIncorpSubscription":{
      |      "callbackUrl":"www.testUpdate.com"
      |    },
      |    "IncorpStatusEvent":{
      |      "status":"rejected",
      |      "description":"description"
      |    }
      |  }
      |}
    """.stripMargin)

  "setupSubscription" should {

    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
      stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
      stubGet("/auth/ids", 200, """{"internalId":"Int-xxx","externalId":"Ext-xxx"}""")
    }

    "return a 202 HTTP response" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post(json).futureValue
      response.status shouldBe 202
    }

    "return a 200 HTTP response when an existing IncorpUpdate is fetched" in new Setup {
      setupSimpleAuthMocks()
      insert(incorpUpdate)
      incCount shouldBe 1

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post(jsonUpdate).futureValue
      response.status shouldBe 200

      val (_, jsonNoTs) = extractTimestamp(response.json.as[JsObject])
      jsonNoTs shouldBe jsonIncorpUpdate

    }

  }

  "getSubscription" should {

    "return a 200 HTTP response when a subscription with the given info exists" in new Setup {
      await(repository.insertSub(sub))

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").get.futureValue
      response.status shouldBe 200
    }

    "return a 404 HTTP response when a subscription with the given info does not exist" in new Setup {

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").get.futureValue
      response.status shouldBe 404
    }
  }




}

