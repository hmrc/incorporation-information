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

package apis.test

import com.google.inject.AbstractModule
import helpers.IntegrationSpecBase
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.play.json.collection.JSONCollection
import repositories._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class IncorpUpdateTestEndpointISpec extends IntegrationSpecBase with MongoSpecSupport {

  val mockUrl = s"http://$wiremockHost:$wiremockPort"

  val additionalConfiguration = Map(
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "application.router" -> "testOnlyDoNotUseInAppConf.Routes"
  )
  val mongoTest = mongo
  val dbNameExtraForDbToBeUnique = ("iu" -> "qr")
  def rmComp(dbNameExtra:String) = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest.copy(mongoUri + dbNameExtra)
  }
  lazy val incRepo = new IncorpUpdateMongoRepository(mongoTest, IncorpUpdate.mongoFormat){
    override lazy val collection: JSONCollection = mongoTest().collection[JSONCollection](databaseName + dbNameExtraForDbToBeUnique._1)
  }
  lazy val queueRepository = new QueueMongoRepository(mongoTest, QueuedIncorpUpdate.format){

    override lazy val collection: JSONCollection = mongoTest().collection[JSONCollection](databaseName + dbNameExtraForDbToBeUnique._2)
  }
  object m extends AbstractModule {
    override def configure(): Unit = {
      bind(classOf[QueueMongo]).toInstance(QueueMongoFake)
      bind(classOf[IncorpUpdateMongo]).toInstance(IncorpUpdateMongoFake)
    }
  }
   object IncorpUpdateMongoFake extends IncorpUpdateMongo {
    override lazy val repo: IncorpUpdateMongoRepository = incRepo
    override lazy val mongo: ReactiveMongoComponent = rmComp(dbNameExtraForDbToBeUnique._1)
  }
  object QueueMongoFake extends QueueMongo {
    override lazy val repo: QueueMongoRepository = queueRepository
    override lazy val mongo: ReactiveMongoComponent = rmComp(dbNameExtraForDbToBeUnique._2)
  }
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig(additionalConfiguration))
    .overrides(m)
    .build

  private def client(path: String) =
    ws.url(s"http://localhost:$port/incorporation-information/$path").
      withFollowRedirects(false)

  class Setup {
    val i = IncorpUpdate("foo","bar",None,None,"",None)
    await(incRepo.drop)
    await(incRepo.ensureIndexes)
    await(incRepo.insert(IncorpUpdate("foo","bar",None,None,"",None)))
    await(incRepo.remove("_id" -> "foo"))
    await(incRepo.count) shouldBe 0
    await(queueRepository.drop)
    await(queueRepository.ensureIndexes)
    await(queueRepository.insert(QueuedIncorpUpdate(DateTime.now(),i)))
    await(queueRepository.removeAll())
    await(queueRepository.count) shouldBe 0
    def getIncorp(txid: String) = await(incRepo.getIncorpUpdate(txid))
  }

  override def afterEach() = new Setup {
    await(incRepo.drop)
  }

  val transactionId = "123abc"

  "IncorpUpdate add test Endpoint" should {

    def setupSimpleAuthMocks() = {
      stubPost("/write/audit", 200, """{"x":2}""")
    }

    "Allow an IU with tx-id and defaults" in new Setup {
      setupSimpleAuthMocks()

      await(incRepo.count) shouldBe 0
      await(queueRepository.count) shouldBe 0

      val response = client(s"test-only/add-incorp-update?txId=${transactionId}").get().futureValue
      response.status shouldBe 200

      await(incRepo.count) shouldBe 1
      await(queueRepository.count) shouldBe 1
      getIncorp(transactionId) shouldBe Some(IncorpUpdate("123abc","rejected",None,None,"-1",None))
    }

    def toDateTime(d:String) = ISODateTimeFormat.dateParser().withOffsetParsed().parseDateTime(d)

    "Allow a successful IU" in new Setup {
      val incorpDate = "2017-02-01"

      setupSimpleAuthMocks()

      await(incRepo.find()).size shouldBe 0
      await(queueRepository.find()).size shouldBe 0

      val response = client(s"test-only/add-incorp-update?txId=${transactionId}&date=${incorpDate}&success=true&crn=1234").get().futureValue
      response.status shouldBe 200

      await(incRepo.find()).size shouldBe 1
      await(queueRepository.find()).size shouldBe 1
      getIncorp(transactionId) shouldBe Some(IncorpUpdate("123abc","accepted",Some("1234"),Some(toDateTime(incorpDate)),"-1",None))
    }
  }
}