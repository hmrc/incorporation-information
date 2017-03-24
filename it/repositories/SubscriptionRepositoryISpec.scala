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

import helpers.SCRSMongoSpec
import models.Subscription
import play.modules.reactivemongo.MongoDbConnection

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionRepositoryISpec extends SCRSMongoSpec {

  val testValid = sub()

  def construct() =
    Subscription(
      "transId1",
      "test",
      "CT",
      "url"
    )

  def sub() = Subscription(
    "transId1",
    "test",
    "CT",
    "url"
  )


  class Setup extends MongoDbConnection {
    val repository = new SubscriptionsMongoRepository(db)
    await(repository.drop)
    await(repository.ensureIndexes)

    def insert(sub: Subscription) = await(repository.insert(sub))
    def count = await(repository.count)
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
      result shouldBe SuccessfulSub
    }

    "update an existing sub that matches the selector" in new Setup {
      insert(sub)
      count shouldBe 1

      val result = await(repository.insertSub(sub))
      count shouldBe 1
      result shouldBe SuccessfulSub
    }

  }

  "wipeTestData" should {
    "remove all test data from submissions status" in new Setup {
      val result = await(repository.wipeTestData())
      result.hasErrors shouldBe false
    }
  }
}
