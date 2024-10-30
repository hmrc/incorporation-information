/*
 * Copyright 2024 HM Revenue & Customs
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

package test.repositories

import models.Subscription
import org.mongodb.scala.bson.BsonDocument
import play.api.test.Helpers._
import repositories.{SubscriptionsMongo, UpsertResult}
import test.helpers.SCRSMongoSpec

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

  lazy val repository = app.injector.instanceOf[SubscriptionsMongo].repo

  class Setup {

    def insert(sub: Subscription) = await(repository.insertSub(sub))
    def count = await(repository.collection.countDocuments().toFuture())
    def drop = await(repository.collection.drop().toFuture())

    drop
    await(repository.ensureIndexes)
  }

  val testKey = "testKey"

  "getSubscriptionStats" must {
    "return an empty Map if the collection is empty" in new Setup {
      count mustBe 0

      await(repository.getSubscriptionStats()) mustBe Map()
    }

    "return an single metric for a single subscription" in new Setup {
      insert(testValid)
      await(repository.collection.countDocuments().toFuture()) mustBe 1

      await(repository.getSubscriptionStats()) mustBe Map(testValid.regime -> 1)
    }

    "return an metrics for multiple subscriptions" in new Setup {

      await(repository.insertSub(Subscription("tx1", "r1", "s1", "url1")))
      await(repository.insertSub(Subscription("tx2", "r2", "s1", "url2")))
      await(repository.insertSub(Subscription("tx3", "r2", "s2", "url3")))
      await(repository.insertSub(Subscription("tx4", "r3", "s1", "url4")))
      await(repository.insertSub(Subscription("tx5", "r3", "s2", "url5")))
      await(repository.insertSub(Subscription("tx6", "r3", "s3", "url6")))
      await(repository.collection.countDocuments().toFuture()) mustBe 6

      await(repository.getSubscriptionStats()) mustBe Map("r1" -> 1, "r2" -> 2, "r3" -> 3)
    }

  }

  "getSubscriptions" must {
    "return a submissions" in new Setup {
      count mustBe 0
      insert(testValid)
      await(repository.collection.countDocuments().toFuture()) mustBe 1
      val result = await(repository.getSubscription("transId1", "test", "CT"))
      result.head.subscriber mustBe "CT"
    }
  }

  "insertSub" must {

    "return an Upsert Result" in new Setup {
      val result = insert(testValid)
      val expected = UpsertResult(0, 1, Seq())
      result mustBe expected

    }

    "update an existing sub that matches the selector" in new Setup {
      insert(sub)
      count mustBe 1

      val result = await(repository.insertSub(sub))
      count mustBe 1
      result mustBe UpsertResult(1, 0, Seq())
    }


    "update the callback url when an already existing Subscription is updated with a new call back url" in new Setup {
      val firstResponse = await(repository.insertSub(sub))
      val secondResponse = await(repository.insertSub(subUpdate()))

      firstResponse mustBe UpsertResult(0, 1, Seq())
      secondResponse mustBe UpsertResult(1, 0, Seq())

    }

  }

  "deletesub" must {
    "only delete a single subscription" in new Setup {
      count mustBe 0
      await(repository.insertSub(sub))
      await(repository.insertSub(secondSub))
      count mustBe 2
      await(repository.deleteSub("transId1", "test", "CT"))
      await(repository.collection.countDocuments().toFuture()) mustBe 1

      val result = await(repository.getSubscription("transId1", "test", "PAYE"))
      result.head.subscriber mustBe "PAYE"
    }

    "not delete a subscription when the subscription does not exist" in new Setup {
      count mustBe 0
      await(repository.insertSub(sub))
      await(repository.insertSub(secondSub))
      count mustBe 2
      await(repository.deleteSub("transId1", "test", "CTabc"))
      count mustBe 2
    }

    "try to delete a subscription from an empty collection" in new Setup {
      count mustBe 0
      val res = await(repository.deleteSub("transId1", "test", "CTabc"))
      res.getDeletedCount mustBe 0
    }
  }

  "getSubscription" must {
    "return a subscription if one exists with the given information" in new Setup {
      await(repository.insertSub(sub))

      val result = await(repository.getSubscription(sub.transactionId, sub.regime, sub.subscriber))
      result.head.transactionId mustBe sub.transactionId
      result.head.regime mustBe sub.regime
      result.head.subscriber mustBe sub.subscriber
    }

    "return None if no subscription exists with the given information" in new Setup {
      count mustBe 0

      val result = await(repository.getSubscription(sub.transactionId, sub.regime, sub.subscriber))
      result mustBe None
    }
  }

  "getSubscriptionsByRegime" must {

    val testRegime = "archy"
    val limit = 10

    "an empty list if no documents exist for a regime" in new Setup {
      val result = await(repository.getSubscriptionsByRegime(testRegime, limit))
      result mustBe Seq()
    }
    "a list containing one document if there is only one in the database, even if the max is higher" in new Setup {
      val subscription = subRegime(testRegime, 1)
      await(repository.insertSub(subscription))

      val result = await(repository.getSubscriptionsByRegime(testRegime, limit))
      result mustBe List(subscription)
    }
    "a list containing subscriptions up to the desired limit, even if the more were returned" in new Setup {
      val subscriptions = ((1 to 15) map (num => subRegime(testRegime, num))).toList
      subscriptions foreach { sub => await(repository.insertSub(sub)) }

      val expected = subscriptions.take(limit)

      val result = await(repository.getSubscriptionsByRegime(testRegime, limit))
      result mustBe expected
    }
  }
}
