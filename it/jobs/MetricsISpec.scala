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
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo, TimepointMongo}
import uk.gov.hmrc.play.scheduling.ScheduledJob

import scala.concurrent.ExecutionContext.Implicits._

class MetricsISpec extends IntegrationSpecBase {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "metrics.enabled" -> "true",
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  class Setup {
    val subRepo = app.injector.instanceOf[SubscriptionsMongo].repo

    def insert(s: Subscription) = await(subRepo.collection.insert(s)(Subscription.format, global))
  }

  override def beforeEach() = new Setup {
    Seq(subRepo) map { r =>
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


  "If disabled, update metrics" should {
    "refuse to run" in new Setup {
      setupAuditMocks()
      setupFeatures(scheduledMetrics = false)

      val job = lookupJob("metrics-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("Feature is turned off")
    }
  }

  "If enabled, update metrics" should {
    "do no updates if no subscriptions" in new Setup {
      setupAuditMocks()
      setupFeatures(scheduledMetrics = true)

      await(subRepo.collection.count()) shouldBe 0

      val job = lookupJob("metrics-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("metrics-job")

    }
  }

  "If enabled, update metrics" should {
    "do a single update if one subscriptions" in new Setup {
      setupAuditMocks()
      setupFeatures(scheduledMetrics = true)

      await(insert(Subscription("tx1", "regime", "sub1", "url1")))
      await(subRepo.collection.count()) shouldBe 1

      val job = lookupJob("metrics-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("metrics-job")
    }
  }
}
