/*
* Copyright 2016 HM Revenue & Customs
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

package repositories

import model.Subscription
import mongo.{MongoSubscriptionsRepository}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionRepositoryISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll with WithFakeApplication {

  val testValid = construct()


  def construct() =
    Subscription(
      "CT",
      "test",
      "transId1"
    )


  class Setup {
    val db = fakeApplication.injector.instanceOf(classOf[ReactiveMongoComponent])
    val repository = new MongoSubscriptionsRepository
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  val testKey = "testKey"

  "getSubscriptions" should {
    "return a submissions" in new Setup {
      await(repository.count) shouldBe 0
      await(repository.insertSub(testValid))
      await(repository.count) shouldBe 1
      val result = await(repository.getSubscription("transId1"))
      result.head.subscriber shouldBe "CT"
    }
  }

  "insertSub" should {
    "return a WriteResult" in new Setup {
      val result = await(repository.insertSub(testValid))
      result.hasErrors shouldBe false
    }

  }

  "wipeTestData" should {
    "remove all test data from submissions status" in new Setup {
      val result = await(repository.wipeTestData())
      result.hasErrors shouldBe false
    }
  }
}
