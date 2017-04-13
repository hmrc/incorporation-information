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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.google.inject.name.Names
import helpers.IntegrationSpecBase
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONDocument
import reactivemongo.core.errors.GenericDatabaseException
import repositories.{DocValidator, IncorpUpdateMongo, QueueMongo, TimepointMongo}
import uk.gov.hmrc.play.scheduling.ScheduledJob

import scala.concurrent.ExecutionContext.Implicits.global

class IncorporationUpdateISpec extends IntegrationSpecBase {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "microservice.services.incorp-update-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/ifes/submission",
    "microservice.services.incorp-update-api.url" -> "N/A",
    "microservice.services.incorp-update-api.token" -> "N/A",
    "microservice.services.incorp-update-api.itemsToFetch" -> "3"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build

  class Setup {
    val incorpRepo = app.injector.instanceOf[IncorpUpdateMongo].repo
    val timepointRepo = app.injector.instanceOf[TimepointMongo].repo
    val queueRepo = app.injector.instanceOf[QueueMongo].repo

    def insert(u: QueuedIncorpUpdate) = await(queueRepo.collection.insert(u)(QueuedIncorpUpdate.format, global))
  }

  override def beforeEach() = new Setup {
    Seq(incorpRepo, timepointRepo, queueRepo) map { r =>
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

  def jsonItem(txId: String, timepoint: String = "23456", crn: String = "bar", txFieldName: String = "transaction_id") =
    s"""{
       |"company_number":"${crn}",
       |"transaction_status":"accepted",
       |"transaction_type":"incorporation",
       |"company_profile_link":"N/A",
       |"${txFieldName}":"${txId}",
       |"incorporated_on":"2016-08-10",
       |"timepoint":"${timepoint}"
       |}""".stripMargin

  def chResponse(items: String) = s"""{"items":[${items}], "links":{"next":"xxx"}}"""

  "incorp update check with no data" should {

    "Should do no processing when disabled" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = false)

      val job = lookupJob("incorp-update-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("Feature is turned off")
    }

    "Should process successfully when enabled" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = true)

      val emptyChResponse = chResponse("")
      stubGet("/ifes/submission.*", 200, emptyChResponse)

      await(incorpRepo.collection.count()) shouldBe 0

      val job = lookupJob("incorp-update-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("incorp-updates-job")

      await(incorpRepo.collection.count()) shouldBe 0
    }
  }

  "incorp update check with some data" should {
    "insert one" in new Setup {
      setupAuditMocks()
      setupFeatures(submissionCheck = true)

      val timepoint = "23456"
      val response = chResponse(jsonItem("12345", timepoint))
      stubGet("/ifes/submission.*", 200, response)

      await(incorpRepo.collection.count()) shouldBe 0
      await(timepointRepo.retrieveTimePoint) shouldBe None

      val job = lookupJob("incorp-update-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("incorp-updates-job")

      await(incorpRepo.collection.count()) shouldBe 1
      await(timepointRepo.retrieveTimePoint) shouldBe Some(timepoint)
      verify(getRequestedFor(urlMatching("/ifes/submission.*")).
        withQueryParam("timepoint", absent).
        withQueryParam("items_per_page", equalTo("3")))
    }

    "shouldn't update the timepoint if there's an error in processing" in new Setup with DocValidator {
      setupAuditMocks()
      setupFeatures(submissionCheck = true)

      await(validateCRN("^bar[12345679]$"))

      val (tp1, tp2, tp3) = ("12345", "23456", "34567")
      val items = jsonItem("12345", tp2, "bar1") + "," + jsonItem("23456", tp3, "bar8")
      val response = chResponse(items)
      stubGet("/ifes/submission.*", 200, response)
      await(timepointRepo.updateTimepoint(tp1))

      val job = lookupJob("incorp-update-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("incorp-updates-job")

      await(incorpRepo.collection.count()) shouldBe 1

      await(timepointRepo.retrieveTimePoint) shouldBe Some(tp1)
      verify(getRequestedFor(urlMatching("/ifes/submission.*")).
        withQueryParam("timepoint", equalTo(tp1)))
    }

    "not re-insert a document if it exists (but process ok)" in new Setup {
      setupFeatures(submissionCheck = true)

      val (tx1, tx2) = ("12345", "23456")
      val (tp1, tp2) = ("12345", "23456")

      import reactivemongo.json._

      val item = Json.parse(jsonItem(tx1, txFieldName = "_id")).as[JsObject]
      await(incorpRepo.collection.insert(item))
      await(timepointRepo.updateTimepoint(tp1))
      await(incorpRepo.collection.count()) shouldBe 1

      val items = jsonItem(tx1, tp1) + "," + jsonItem(tx2, tp2, "bar8")
      val response = chResponse(items)
      stubGet("/ifes/submission.*", 200, response)

      val job = lookupJob("incorp-update-job")

      val f = job.execute
      val r = await(f)
      r shouldBe job.Result("incorp-updates-job")

      await(incorpRepo.collection.count()) shouldBe 2

      await(timepointRepo.retrieveTimePoint) shouldBe Some(tp2)
      verify(getRequestedFor(urlMatching("/ifes/submission.*")).
        withQueryParam("timepoint", equalTo(tp1)))
    }

  }

  "incorp update queue" should {
    "not accept duplicate incorp update documents" in new Setup {
      setupFeatures(submissionCheck = true)

      val iu = IncorpUpdate("1234", "awaiting", None, None, "tp", None)
      val qiu1 = QueuedIncorpUpdate(DateTime.now, iu)

      insert(qiu1)
      await(queueRepo.collection.count()) shouldBe 1

      val result = await(queueRepo.bulkInsert(Seq(qiu1)))
      result.writeErrors.head.code shouldBe 11000
      await(queueRepo.collection.count()) shouldBe 1
    }
  }

}
