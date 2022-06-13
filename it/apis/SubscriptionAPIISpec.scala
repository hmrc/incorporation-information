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

package apis

import helpers.IntegrationSpecBase
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.{DateTime, Minutes}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json, __}
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo}

import scala.concurrent.ExecutionContext
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
    .configure(fakeConfig(additionalConfiguration))
    .build

  private def client(path: String) =
    ws.url(s"http://localhost:$port/incorporation-information/$path").
      withFollowRedirects(false)

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  class Setup {
    val incRepo = new IncorpUpdateMongo {
      override implicit val ec: ExecutionContext = global
      override val mongo: ReactiveMongoComponent = reactiveMongoComponent
    }.repo
    val repository = new SubscriptionsMongo(reactiveMongoComponent).repo
    val queueRepo = new QueueMongo {
      override implicit val ec: ExecutionContext = global
      override val mongo: ReactiveMongoComponent = reactiveMongoComponent
    }.repo

    def insert(sub: Subscription) = await(repository.insert(sub))
    def insert(update: IncorpUpdate) = await(incRepo.insert(update))
    def insert(queuedIncorpUpdate: QueuedIncorpUpdate) = await(queueRepo.insert(queuedIncorpUpdate))
    def subCount = await(repository.count)
    def incCount = await(incRepo.count)
    def queueCount = await(queueRepo.count)
    def getQueuedIncorp(txid: String) = await(queueRepo.getIncorpUpdate(txid))
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
  val crn = "crn-12345"
  val incorpDate = DateTime.now()

  val sub = Subscription(transactionId, regime, subscriber, url)
  val incorpUpdate = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))
  val acceptedIncorpUpdate = IncorpUpdate(transactionId, "accepted", Some(crn), Some(incorpDate), "tp", Some("description"))
  val acceptedQueuedIncorp = QueuedIncorpUpdate(incorpDate, acceptedIncorpUpdate)
  val queuedIncorp = QueuedIncorpUpdate(incorpDate, incorpUpdate)

  val json = Json.parse(
    """
      |{
      |  "SCRSIncorpSubscription": {
      |    "callbackUrl": "www.test.com"
      |  }
      |}
    """.stripMargin
  )

  val subscription = Json.parse(
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

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post(subscription).futureValue
      response.status shouldBe 200

      val (_, jsonNoTs) = extractTimestamp(response.json.as[JsObject])
      jsonNoTs shouldBe jsonIncorpUpdate

    }

  }

  "forceSubscription" should {

    "force a subscription into II even if the corresponding incorp update exists and insert an incorp update into the queue" in new Setup {
      insert(acceptedIncorpUpdate)
      incCount shouldBe 1
      subCount shouldBe 0
      queueCount shouldBe 0

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      subCount shouldBe 1
      queueCount shouldBe 1

      response.status shouldBe 202
      response.json shouldBe Json.parse("""{"forced":true}""")
    }

    "force a subscription even if the corresponding incorp update and queued update exist" in new Setup {

      insert(acceptedIncorpUpdate)
      insert(queuedIncorp)

      incCount shouldBe 1
      queueCount shouldBe 1
      subCount shouldBe 0

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      subCount shouldBe 1

      response.status shouldBe 202
      response.json shouldBe Json.parse("""{"forced":true}""")
    }

    "still insert a subscription when an incorp update does not exist yet" in new Setup {

      incCount shouldBe 0
      queueCount shouldBe 0
      subCount shouldBe 0

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      subCount shouldBe 1

      response.status shouldBe 202
    }

    "force a subscription that will be processed 5 minutes in the future if an incorp update exists but no queued incorp" in new Setup {

      val now = DateTime.now()

      insert(incorpUpdate)

      client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      queueCount shouldBe 1

      val queuedIncorpTimestamp = getQueuedIncorp(transactionId).get.timestamp

      Minutes.minutesBetween(now, queuedIncorpTimestamp).getMinutes shouldBe 5

    }

    "force a subscription where the queued update exists and is upserted to be processed 5 minutes in the future if an incorp update exists" in new Setup {

      insert(acceptedIncorpUpdate)
      insert(acceptedQueuedIncorp)

      incCount shouldBe 1
      queueCount shouldBe 1

      val oldQueuedIncorpTimestamp = queuedIncorp.timestamp

      client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      queueCount shouldBe 1

      val updatedQueuedIncorpTimestamp = getQueuedIncorp(transactionId).get.timestamp

      Minutes.minutesBetween(oldQueuedIncorpTimestamp, updatedQueuedIncorpTimestamp).getMinutes shouldBe 5

    }
  }
}
