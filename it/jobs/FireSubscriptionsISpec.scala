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

package jobs

import com.google.inject.name.Names
import helpers.IntegrationSpecBase
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo, TimepointMongo}
import uk.gov.hmrc.play.scheduling.ScheduledJob

import scala.concurrent.ExecutionContext.Implicits._

class FireSubscriptionsISpec extends IntegrationSpecBase {

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

  class Setup {
    val incorpRepo = app.injector.instanceOf[IncorpUpdateMongo].repo
    val timepointRepo = app.injector.instanceOf[TimepointMongo].repo
    val queueRepo = app.injector.instanceOf[QueueMongo].repo
    val subRepo = app.injector.instanceOf[SubscriptionsMongo].repo

    def insert(u: QueuedIncorpUpdate) = await(queueRepo.collection.insert(u)(QueuedIncorpUpdate.format, global))
    def insert(s: Subscription) = await(subRepo.collection.insert(s)(Subscription.format, global))
  }

  override def beforeEach() = new Setup {
    Seq(incorpRepo, timepointRepo, queueRepo, subRepo) map { r =>
      await(r.drop)
      await(r.ensureIndexes)
    }
  }

  override def afterEach() = new Setup {
  }

  def setupAuditMocks() = {
    stubPost("/write/audit", 200, """{"x":2}""")
  }

  def lookupJob(name: String): ScheduledJob = {
    val qualifier = Some(QualifierInstance(Names.named(name)))
    val key = BindingKey[ScheduledJob](classOf[ScheduledJob], qualifier)
    app.injector.instanceOf[ScheduledJob](key)
  }


  "fire subscriptions check with no data" should {

    "Should do no processing when disabled" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = false)

      val job = lookupJob("fire-subs-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("Feature is turned off")
    }

    "Should process successfully when enabled" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = true)

      stubPost("/mockUri", 200, "")

      await(incorpRepo.collection.count()) shouldBe 0

      val job = lookupJob("fire-subs-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("fire-subs-job")

      await(incorpRepo.collection.count()) shouldBe 0
    }

    "Both submission and queue repos should be empty after job has been fired" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = true)

      stubPost("/mockUri", 200, "")

      await(queueRepo.collection.count()) shouldBe 0
      await(timepointRepo.retrieveTimePoint) shouldBe None

      val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
      val QIU = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
      insert(QIU)
      await(queueRepo.collection.count()) shouldBe 1

      await(subRepo.collection.count()) shouldBe 0
      val sub = Subscription("transId1", "CT", "subscriber", s"$mockUrl/mockUri")
      val sub2 = Subscription("transId1", "PAYE", "subscriber", s"$mockUrl/mockUri")
      insert(sub)
      await(subRepo.collection.count()) shouldBe 1
      insert(sub2)
      await(subRepo.collection.count()) shouldBe 2


      val job = lookupJob("fire-subs-job")

      val f = job.execute
      val r = await(f)

      await(subRepo.collection.count()) shouldBe 0
      await(queueRepo.collection.count()) shouldBe 0
      r shouldBe job.Result("fire-subs-job")

    }

    "not be able to run two jobs at the same time" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = true)

      stubPost("/mockUri", 200, "")

      await(queueRepo.collection.count()) shouldBe 0

      val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
      val QIU = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
      insert(QIU)
      await(queueRepo.collection.count()) shouldBe 1

      await(subRepo.collection.count()) shouldBe 0
      val sub = Subscription("transId1", "CT", "subscriber", s"$mockUrl/mockUri")
      val sub2 = Subscription("transId1", "PAYE", "subscriber", s"$mockUrl/mockUri")
      insert(sub)
      await(subRepo.collection.count()) shouldBe 1
      insert(sub2)
      await(subRepo.collection.count()) shouldBe 2

      val job = lookupJob("fire-subs-job")
      val f = job.execute
      val f2 = await(job.execute)

      f2 shouldBe job.Result("Skipping execution: job running")
    }
  }



}
