/*
* Copyright 2021 HM Revenue & Customs
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
import play.api.test.Helpers._

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

  def sub(num: Int) = (1 to num).map(n => Subscription(
    s"transId$n",
    s"regime$n",
    s"sub$n",
    s"url$n"
  ))

  def sub() = Subscription(
    "transId1",
    "test",
    "CT",
    "url"
  )

  def subRegime(regime: String, num: Int) = Subscription(
    s"transId$num",
    regime,
    "CT",
    "url"
  )

  def secondSub() = Subscription(
    "transId1",
    "test",
    "PAYE",
    "url"
  )

  def subUpdate() = Subscription(
    "transId1",
    "test",
    "CT",
    "newUrl"
  )


  class Setup {
    val repository = new SubscriptionsMongoRepository(reactiveMongoComponent.mongoConnector.db)
    await(repository.drop)
    await(repository.ensureIndexes)

    def insert(sub: Subscription) = await(repository.insert(sub))

    def count = await(repository.count)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  val testKey = "testKey"

  "getSubscriptionStats" should {
    "return an empty Map if the collection is empty" in new Setup {
      await(repository.count) shouldBe 0

      await(repository.getSubscriptionStats()) shouldBe Map()
    }

    "return an single metric for a single subscription" in new Setup {
      await(repository.insertSub(testValid))
      await(repository.count) shouldBe 1

      await(repository.getSubscriptionStats()) shouldBe Map(testValid.regime -> 1)
    }

    "return an metrics for multiple subscriptions" in new Setup {

      await(repository.insertSub(Subscription("tx1", "r1", "s1", "url1")))
      await(repository.insertSub(Subscription("tx2", "r2", "s1", "url2")))
      await(repository.insertSub(Subscription("tx3", "r2", "s2", "url3")))
      await(repository.insertSub(Subscription("tx4", "r3", "s1", "url4")))
      await(repository.insertSub(Subscription("tx5", "r3", "s2", "url5")))
      await(repository.insertSub(Subscription("tx6", "r3", "s3", "url6")))
      await(repository.count) shouldBe 6

      await(repository.getSubscriptionStats()) shouldBe Map("r1" -> 1, "r2" -> 2, "r3" -> 3)
    }

  }

  "getSubscriptions" should {
    "return a submissions" in new Setup {
      await(repository.count) shouldBe 0
      await(repository.insertSub(testValid))
      await(repository.count) shouldBe 1
      val result = await(repository.getSubscription("transId1", "test", "CT"))
      result.head.subscriber shouldBe "CT"
    }
  }

  "insertSub" should {

    "return an Upsert Result" in new Setup {
      val result = await(repository.insertSub(testValid))
      val expected = UpsertResult(0, 1, Seq())
      result shouldBe expected

    }

    "update an existing sub that matches the selector" in new Setup {
      insert(sub)
      count shouldBe 1

      val result = await(repository.insertSub(sub))
      count shouldBe 1
      result shouldBe UpsertResult(1, 0, Seq())
    }


    "update the callback url when an already existing Subscription is updated with a new call back url" in new Setup {
      val firstResponse = await(repository.insertSub(sub))
      val secondResponse = await(repository.insertSub(subUpdate()))

      firstResponse shouldBe UpsertResult(0, 1, Seq())
      secondResponse shouldBe UpsertResult(1, 0, Seq())

    }

  }

  "deletesub" should {
    "only delete a single subscription" in new Setup {
      await(repository.count) shouldBe 0
      await(repository.insertSub(sub))
      await(repository.insertSub(secondSub))
      await(repository.count) shouldBe 2
      await(repository.deleteSub("transId1", "test", "CT"))
      await(repository.count) shouldBe 1

      val result = await(repository.getSubscription("transId1", "test", "PAYE"))
      result.head.subscriber shouldBe "PAYE"
    }

    "not delete a subscription when the subscription does not exist" in new Setup {
      await(repository.count) shouldBe 0
      await(repository.insertSub(sub))
      await(repository.insertSub(secondSub))
      await(repository.count) shouldBe 2
      await(repository.deleteSub("transId1", "test", "CTabc"))
      await(repository.count) shouldBe 2
    }

    "try to delete a subscription from an empty collection" in new Setup {
      await(repository.drop)
      await(repository.count) shouldBe 0
      val res = await(repository.deleteSub("transId1", "test", "CTabc"))
      (res.ok, res.n) shouldBe(true, 0)
    }
  }

  "getSubscription" should {
    "return a subscription if one exists with the given information" in new Setup {
      await(repository.insertSub(sub))

      val result = await(repository.getSubscription(sub.transactionId, sub.regime, sub.subscriber))
      result.head.transactionId shouldBe sub.transactionId
      result.head.regime shouldBe sub.regime
      result.head.subscriber shouldBe sub.subscriber
    }

    "return None if no subscription exists with the given information" in new Setup {
      await(repository.count) shouldBe 0

      val result = await(repository.getSubscription(sub.transactionId, sub.regime, sub.subscriber))
      result shouldBe None
    }
  }


  "wipeTestData" should {
    "remove all test data from submissions status" in new Setup {
      val result = await(repository.wipeTestData())
      result.writeErrors.isEmpty shouldBe true
      result.writeConcernError.isEmpty shouldBe true
    }
  }

  "getSubscriptionsByRegime" should {

    val testRegime = "archy"
    val limit = 10

    "an empty list if no documents exist for a regime" in new Setup {
      val result = await(repository.getSubscriptionsByRegime(testRegime, limit))
      result shouldBe Seq()
    }
    "a list containing one document if there is only one in the database, even if the max is higher" in new Setup {
      val subscription = subRegime(testRegime, 1)
      await(repository.insertSub(subscription))

      val result = await(repository.getSubscriptionsByRegime(testRegime, limit))
      result shouldBe List(subscription)
    }
    "a list containing subscriptions up to the desired limit, even if the more were returned" in new Setup {
      val subscriptions = ((1 to 15) map (num => subRegime(testRegime, num))).toList
      subscriptions foreach { sub => await(repository.insertSub(sub)) }

      val expected = subscriptions.take(limit)

      val result = await(repository.getSubscriptionsByRegime(testRegime, limit))
      result shouldBe expected
    }
  }
}
