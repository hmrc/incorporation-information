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

package jobs

import com.google.inject.name.Names
import helpers.IntegrationSpecBase
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.test.Helpers._
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo, TimepointMongo}
import utils.DateCalculators

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits._

class FireSubscriptionsISpec extends IntegrationSpecBase with DateCalculators {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "schedules.fire-subs-job.lockTimeout" -> "200",
    "schedules.fire-subs-job.expression" -> "0_0_0_1_1_?_2099/1",
    "schedules.fire-subs-job.enabled" -> "true",
    "schedules.incorp-update-job.enabled" -> "false",
    "schedules.proactive-monitoring-job.enabled" -> "false",
    "schedules.metrics-job.enabled" -> "false"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .build

  class Setup {
    val incorpRepo = app.injector.instanceOf[IncorpUpdateMongo].repo
    val timepointRepo = app.injector.instanceOf[TimepointMongo].repo
    val queueRepo = app.injector.instanceOf[QueueMongo].repo
    val subRepo = app.injector.instanceOf[SubscriptionsMongo].repo
    val lockRepo = app.injector.instanceOf[LockRepositoryProvider].repo

    def insert(u: QueuedIncorpUpdate) = await(queueRepo.upsertIncorpUpdate(u))
    def insert(s: Subscription) = await(subRepo.insertSub(s))

    Seq(incorpRepo, timepointRepo, queueRepo, subRepo) map { r =>
      await(r.collection.drop.toFuture())
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

  "fire subscriptions check with no data" must {

    "Should process successfully when enabled" in new Setup {
      setupAuditMocks()
      setupFeatures(fireSubscriptions = true)

      stubPost("/mockUri", 200, "")

      await(incorpRepo.collection.countDocuments().toFuture()) mustBe 0

      val job = lookupJob("fire-subs-job")

      val f = job.schedule
      f mustBe true
      await(job.scheduledMessage.service.invoke)

      await(incorpRepo.collection.countDocuments().toFuture()) mustBe 0
    }
  }

  "fire subscriptions check with data" must {
    "Both submission and queue repos should be empty after job has been fired" in new Setup {
      setupAuditMocks()
      setupFeatures(fireSubscriptions = true)

      stubPost("/mockUri", 200, "")

      await(queueRepo.collection.countDocuments().toFuture()) mustBe 0
      await(timepointRepo.retrieveTimePoint) mustBe None

      val incorpUpdate = IncorpUpdate("transId1", "awaiting", None, None, "timepoint", None)
      val QIU = QueuedIncorpUpdate(getDateTimeNowUTC, incorpUpdate)
      insert(QIU)
      await(queueRepo.collection.countDocuments().toFuture()) mustBe 1

      await(subRepo.collection.countDocuments().toFuture()) mustBe 0
      val sub = Subscription("transId1", "CT", "subscriber", s"$mockUrl/mockUri")
      val sub2 = Subscription("transId1", "PAYE", "subscriber", s"$mockUrl/mockUri")
      insert(sub)
      await(subRepo.collection.countDocuments().toFuture()) mustBe 1
      insert(sub2)
      await(subRepo.collection.countDocuments().toFuture()) mustBe 2

      val job = lookupJob("fire-subs-job")

      val res = await(job.scheduledMessage.service.invoke.map(_.asInstanceOf[Either[Seq[Boolean], LockResponse]]))

      res.left.get mustBe Seq(true)
      await(subRepo.collection.countDocuments().toFuture()) mustBe 0
      await(queueRepo.collection.countDocuments().toFuture()) mustBe 0
    }
  }
}