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

package apis.test

import helpers.IntegrationSpecBase
import models.IncorpUpdate
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.{Application, inject}
import repositories._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import utils.TimestampFormats

import java.time.LocalDateTime

class IncorpUpdateTestEndpointISpec extends IntegrationSpecBase with MongoSupport {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  )

  lazy val incRepo = app.injector.instanceOf[IncorpUpdateMongoImpl].repo
  lazy val queueRepository = app.injector.instanceOf[QueueMongoImpl].repo

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .overrides(inject.bind[MongoComponent].toInstance(mongoComponent))
    .build

  private def client(path: String) =
    ws.url(s"http://localhost:$port/incorporation-information/$path").
      withFollowRedirects(false)

  class Setup {
    await(incRepo.collection.drop().toFuture())
    await(incRepo.ensureIndexes)
    await(queueRepository.collection.drop.toFuture())
    await(queueRepository.ensureIndexes)
    def getIncorp(txid: String) = await(incRepo.getIncorpUpdate(txid))
  }

  override def afterEach() = new Setup { }

  val transactionId = "123abc"

  "IncorpUpdate add test Endpoint" must {

    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
    }

    "Allow an IU with tx-id and defaults" in new Setup {
      setupSimpleAuthMocks()

      await(incRepo.collection.countDocuments().toFuture()) mustBe 0
      await(queueRepository.collection.countDocuments().toFuture()) mustBe 0

      val response = client(s"test-only/add-incorp-update?txId=${transactionId}").get().futureValue
      response.status mustBe 200

      await(incRepo.collection.countDocuments().toFuture()) mustBe 1
      await(queueRepository.collection.countDocuments().toFuture()) mustBe 1
      getIncorp(transactionId) mustBe Some(IncorpUpdate("123abc", "rejected", None, None, "-1", None))
    }

    def toDateTime(d: String) = LocalDateTime.parse(d, TimestampFormats.ldtFormatter)

    "Allow a successful IU" in new Setup {
      val incorpDate = "2017-02-01"

      setupSimpleAuthMocks()

      await(incRepo.collection.find().toFuture()).size mustBe 0
      await(queueRepository.collection.find().toFuture()).size mustBe 0

      val response = client(s"test-only/add-incorp-update?txId=${transactionId}&date=${incorpDate}&success=true&crn=1234").get().futureValue
      response.status mustBe 200

      await(incRepo.collection.find().toFuture()).size mustBe 1
      await(queueRepository.collection.find().toFuture()).size mustBe 1
      getIncorp(transactionId) mustBe Some(IncorpUpdate("123abc", "accepted", Some("1234"), Some(toDateTime(incorpDate)), "-1", None))
    }
  }
}