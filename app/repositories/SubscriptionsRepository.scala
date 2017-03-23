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

package repositories

import javax.inject.Singleton

import models.Subscription
import models.IncorpUpdate
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


//object SubscriptionsRepository extends MongoDbConnection {
//  private lazy val repository = new MongoSubscriptionsRepository
//
//  def apply() : SubscriptionsRepository = repository
//}

@Singleton
class SubscriptionsMongo extends MongoDbConnection with ReactiveMongoFormats {
  val store = new SubscriptionsMongoRepository(db)
}

trait SubscriptionsRepository extends Repository[Subscription, BSONObjectID] {
  def insertSub(sub: Subscription) : Future[UpsertResult]

  def getSubscription(transactionId: String) : Future[Option[Subscription]]

  def wipeTestData() : Future[WriteResult]
}


sealed trait SubscriptionStatus
case object SuccessfulSub extends SubscriptionStatus
case object FailedSub extends SubscriptionStatus
case class IncorpExists(update: IncorpUpdate) extends SubscriptionStatus


class SubscriptionsMongoRepository(mongo: () => DB)
  extends ReactiveRepository[Subscription, BSONObjectID]("subscriptions", mongo, Subscription.format, ReactiveMongoFormats.objectIdFormats)
    with SubscriptionsRepository
{

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("transactionId" -> IndexType.Ascending, "regime" -> IndexType.Ascending, "subscriber" -> IndexType.Ascending),
      name = Some("SubscriptionIdIndex"), unique = true, sparse = false
    )
  )

  def insertSub(sub: Subscription) : Future[SubscriptionStatus] = {
    collection.insert(sub) map {
      wr =>
        wr.n match {
          case 1 => SuccessfulSub
          case _ => {
            logger.error("[MongoSubscriptionsRepository] [insertSub] the subscription was not inserted")
            FailedSub
          }
        }
    }
  }

  def getSubscription(transactionId: String): Future[Option[Subscription]] = {
    val query = BSONDocument("transactionId" -> transactionId)
    collection.find(query).one[Subscription]
  }


  def wipeTestData(): Future[WriteResult] = {
    removeAll(WriteConcern.Acknowledged)
  }

}
