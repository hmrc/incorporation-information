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

import javax.inject.{Inject, Singleton}

import models.{IncorpUpdate, Subscription}
import play.api.libs.json.JsString
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.api.commands._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class SubscriptionsMongo @Inject()(mongo: ReactiveMongoComponent) extends ReactiveMongoFormats {
  val repo = new SubscriptionsMongoRepository(mongo.mongoConnector.db)
}

trait SubscriptionsRepository extends Repository[Subscription, BSONObjectID] {
  def insertSub(sub: Subscription): Future[UpsertResult]

  def deleteSub(transactionId: String, regime: String, subscriber: String): Future[WriteResult]

  def getSubscription(transactionId: String, regime: String, subscriber: String) : Future[Option[Subscription]]

  def getSubscriptions(transactionId: String): Future[Seq[Subscription]]

  def getSubscriptionStats(): Future[Map[String, Int]]

  def wipeTestData(): Future[WriteResult]
}

sealed trait SubscriptionStatus
case object SuccessfulSub extends SubscriptionStatus
case object FailedSub extends SubscriptionStatus
case class IncorpExists(update: IncorpUpdate) extends SubscriptionStatus
case class SubExists(update: IncorpUpdate) extends SubscriptionStatus


sealed trait UnsubscribeStatus
case object DeletedSub extends UnsubscribeStatus
case object NotDeletedSub extends UnsubscribeStatus

case class UpsertResult(modified: Int, inserted: Int, errors: Seq[WriteError])

class SubscriptionsMongoRepository(mongo: () => DB) extends ReactiveRepository[Subscription, BSONObjectID](
    collectionName = "subscriptions",
    mongo = mongo,
    domainFormat = Subscription.format)
    with SubscriptionsRepository
{

  override def indexes: Seq[Index] = {
    import IndexType.{Ascending => Asc}
    Seq(
      Index(
        key = Seq("transactionId" -> Asc, "regime" -> Asc, "subscriber" -> Asc),
        name = Some("SubscriptionIdIndex"), unique = true, sparse = false
      ),
      Index(
        key = Seq("regime" -> Asc), name = Some("regime"), unique = false
      )
    )
  }

  def insertSub(sub: Subscription) : Future[UpsertResult] = {
    val selector = BSONDocument("transactionId" -> sub.transactionId, "regime" -> sub.regime, "subscriber" -> sub.subscriber)
    collection.update(selector, sub, upsert = true) map {
      res =>
        UpsertResult(res.nModified, res.upserted.size, res.writeErrors)
    }
  }

  def deleteSub(transactionId: String, regime: String, subscriber: String): Future[WriteResult] = {
    val selector = BSONDocument("transactionId" -> transactionId, "regime" -> regime, "subscriber" -> subscriber)
     collection.remove(selector)
  }

  def getSubscription(transactionId: String, regime: String, subscriber: String): Future[Option[Subscription]] = {
    val query = BSONDocument("transactionId" -> transactionId, "regime" -> regime, "subscriber" -> subscriber)
    collection.find(query).one[Subscription]
  }

  def getSubscriptions(transactionId: String): Future[Seq[Subscription]] = {
    val query = BSONDocument("transactionId" -> transactionId)
    collection.find(query).cursor[Subscription]().collect[Seq]()
  }

  def getSubscriptionStats(): Future[Map[String, Int]] = {

    import play.api.libs.json._
    import reactivemongo.json.collection.JSONBatchCommands.AggregationFramework.{Group, Match, SumValue, Project}

    // needed to make it pick up the index
    val matchQuery = Match(Json.obj("regime" -> Json.obj("$ne" -> "")))
    // covering query to prevent doc fetch (optimiser would probably spot this anyway and transform the query)
    val project = Project(Json.obj("regime" -> 1, "_id" -> 0))
    // calculate the regime counts
    val group = Group(JsString("$regime"))("count" -> SumValue(1))

    val metrics = collection.aggregate(matchQuery, List(project, group)) map {
        _.documents map {
          d => {
            val regime = (d \ "_id").as[String]
            val count = (d \ "count").as[Int]
            regime -> count
          }
        }
    }

    metrics map {
      _.toMap
    }
  }

  def wipeTestData(): Future[WriteResult] = {
    removeAll(WriteConcern.Acknowledged)
  }
}
