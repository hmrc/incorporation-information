/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json, __}
import play.api.test.Helpers._
import repositories.{IncorpUpdateMongoImpl, QueueMongoImpl, SubscriptionsMongo}
import utils.DateCalculators

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class SubscriptionAPIISpec extends IntegrationSpecBase with DateCalculators {

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

  lazy val incRepo = app.injector.instanceOf[IncorpUpdateMongoImpl].repo
  lazy val repository = app.injector.instanceOf[SubscriptionsMongo].repo
  lazy val queueRepo = app.injector.instanceOf[QueueMongoImpl].repo

  class Setup {

    def insert(sub: Subscription) = await(repository.insertSub(sub))
    def insert(update: IncorpUpdate) = await(incRepo.storeSingleIncorpUpdate(update))
    def insert(queuedIncorpUpdate: QueuedIncorpUpdate) = await(queueRepo.upsertIncorpUpdate(queuedIncorpUpdate))
    def subCount = await(repository.collection.countDocuments().toFuture())
    def incCount = await(incRepo.collection.countDocuments().toFuture())
    def queueCount = await(queueRepo.collection.countDocuments().toFuture())
    def getQueuedIncorp(txid: String) = await(queueRepo.getIncorpUpdate(txid))

    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes)
    await(incRepo.collection.drop().toFuture())
    await(incRepo.ensureIndexes)
    await(queueRepo.collection.drop().toFuture())
    await(queueRepo.ensureIndexes)
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
  val incorpDate = getDateTimeNowUTC

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

  "setupSubscription" must {

    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
      stubGet("/auth/authority", 200, """{"uri":"xxx","credentials":{"gatewayId":"xxx2"},"userDetailsLink":"xxx3","ids":"/auth/ids"}""")
      stubGet("/auth/ids", 200, """{"internalId":"Int-xxx","externalId":"Ext-xxx"}""")
    }

    "return a 202 HTTP response" in new Setup {
      setupSimpleAuthMocks()

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post(json).futureValue
      response.status mustBe 202
    }

    "return a 200 HTTP response when an existing IncorpUpdate is fetched" in new Setup {
      setupSimpleAuthMocks()
      insert(incorpUpdate)
      incCount mustBe 1

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber").post(subscription).futureValue
      response.status mustBe 200

      val (_, jsonNoTs) = extractTimestamp(response.json.as[JsObject])
      jsonNoTs mustBe jsonIncorpUpdate

    }

  }

  "forceSubscription" must {

    "force a subscription into II even if the corresponding incorp update exists and insert an incorp update into the queue" in new Setup {
      insert(acceptedIncorpUpdate)
      incCount mustBe 1
      subCount mustBe 0
      queueCount mustBe 0

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      subCount mustBe 1
      queueCount mustBe 1

      response.status mustBe 202
      response.json mustBe Json.parse("""{"forced":true}""")
    }

    "force a subscription even if the corresponding incorp update and queued update exist" in new Setup {

      insert(acceptedIncorpUpdate)
      insert(queuedIncorp)

      incCount mustBe 1
      queueCount mustBe 1
      subCount mustBe 0

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      subCount mustBe 1

      response.status mustBe 202
      response.json mustBe Json.parse("""{"forced":true}""")
    }

    "still insert a subscription when an incorp update does not exist yet" in new Setup {

      incCount mustBe 0
      queueCount mustBe 0
      subCount mustBe 0

      val response = client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      subCount mustBe 1

      response.status mustBe 202
    }

    "force a subscription that will be processed 5 minutes in the future if an incorp update exists but no queued incorp" in new Setup {

      val now = getDateTimeNowUTC

      insert(incorpUpdate)

      client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      queueCount mustBe 1

      val queuedIncorpTimestamp = getQueuedIncorp(transactionId).get.timestamp

      ChronoUnit.MINUTES.between(now, queuedIncorpTimestamp) mustBe 5

    }

    "force a subscription where the queued update exists and is upserted to be processed 5 minutes in the future if an incorp update exists" in new Setup {

      insert(acceptedIncorpUpdate)
      insert(acceptedQueuedIncorp)

      incCount mustBe 1
      queueCount mustBe 1

      val oldQueuedIncorpTimestamp = queuedIncorp.timestamp

      client(s"subscribe/$transactionId/regime/$regime/subscriber/$subscriber?force=true").post(subscription).futureValue

      queueCount mustBe 1

      val updatedQueuedIncorpTimestamp = getQueuedIncorp(transactionId).get.timestamp

      ChronoUnit.MINUTES.between(oldQueuedIncorpTimestamp, updatedQueuedIncorpTimestamp) mustBe 5

    }
  }
}
