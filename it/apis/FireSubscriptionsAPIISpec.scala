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
import models.{IncorpUpdate, IncorpUpdateResponse, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo}
import services.SubscriptionFiringService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global


class FireSubscriptionsAPIISpec extends IntegrationSpecBase {


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

  lazy val reactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]


  class Setup {
    val subRepo = new SubscriptionsMongo(reactiveMongoComponent).repo
    val queueRepo = new QueueMongo(reactiveMongoComponent).repo

    def insert(sub: Subscription) = await(subRepo.insert(sub))
    def insert(queuedIncorpUpdate: QueuedIncorpUpdate) = await(queueRepo.insert(queuedIncorpUpdate))
    def subCount = await(subRepo.count)
    def queueCount = await(queueRepo.count)

    implicit val hc = HeaderCarrier()
  }

  override def beforeEach() = new Setup {
    await(subRepo.drop)
    await(subRepo.ensureIndexes)
    await(queueRepo.drop)
    await(queueRepo.ensureIndexes)

    subCount shouldBe 0
    queueCount shouldBe 0
  }

  override def afterEach() = new Setup {
    await(subRepo.drop)
    await(queueRepo.drop)
  }

  val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
  val incorpUpdate2 = IncorpUpdate("transId2", "awaiting", None, None, "timepoint", None)
  val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
  val queuedIncorpUpdate2 = QueuedIncorpUpdate(DateTime.now, incorpUpdate2)
  val sub = Subscription("transId1", "CT", "subscriber", s"$mockUrl/mockUri")
  val sub2 = Subscription("transId1", "PAYE", "subscriber", s"$mockUrl/mockUri")
  val sub3 = Subscription("transId2", "CT", "subscriber", s"$mockUrl/mockUri")
  val incorpUpdateResponse = IncorpUpdateResponse("CT", "subscriber", mockUrl, incorpUpdate)


  "fireIncorpUpdateBatch" should {
    "return a Sequence of a true value when one queued incorp update has been successfully fired and both the " +
      "queued incorp update and the subscription have been deleted" in new Setup {
      insert(sub)
      subCount shouldBe 1

      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(true)
    }

    "return a sequence of true when two subscriptions with the same transId have successfully returned 200 HTTP responses and then deleted" +
     "and therefore the queued incorp update is then deleted" in new Setup {
      insert(sub)
      insert(sub2)
      subCount shouldBe 2

      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(true)
    }

    "return a sequence of two true values when two updates have been successfully fired, the first queued incorp update connected to two subscriptions with " +
      "via the same transId, and the second one connected to one subscription" in new Setup {
      insert(sub)
      insert(sub2)
      insert(sub3)
      subCount shouldBe 3

      insert(queuedIncorpUpdate)
      insert(queuedIncorpUpdate2)
      queueCount shouldBe 2

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(true, true)
    }


    "return a true value when an update has been fired that matches the transId of one of the two Subscriptions in the database" in new Setup {
      insert(sub3)
      insert(sub)
      subCount shouldBe 2

      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(true)
      subCount shouldBe 1
    }

    "return a sequence of a false value when the timestamp of an queued incorp update is in the future" in new Setup {
      insert(sub)
      subCount shouldBe 1

      val futureQIU = QueuedIncorpUpdate(DateTime.now.plusMinutes(10), incorpUpdate)
      insert(futureQIU)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 200, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(false)
    }

    "return a sequence of false when the subscriptions for a queued incorp update did not return a 200 response" in new Setup {
      insert(sub)
      insert(sub2)
      subCount shouldBe 2

      insert(queuedIncorpUpdate)
      queueCount shouldBe 1

      val service = app.injector.instanceOf[SubscriptionFiringService]
      stubPost("/mockUri", 400, "")

      val fResult = service.fireIncorpUpdateBatch
      val result = await(fResult)
      result shouldBe Seq(false)
    }



  }



}
