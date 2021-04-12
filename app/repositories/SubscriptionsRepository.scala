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


import javax.inject.Inject
import models.{IncorpUpdate, Subscription}
import play.api.libs.json.{JsObject, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionsMongo @Inject()(mongo: ReactiveMongoComponent) extends ReactiveMongoFormats {
  lazy val repo = new SubscriptionsMongoRepository(mongo.mongoConnector.db)
}

trait SubscriptionsRepository {
  def insertSub(sub: Subscription): Future[UpsertResult]

  def deleteSub(transactionId: String, regime: String, subscriber: String): Future[WriteResult]

  def getSubscription(transactionId: String, regime: String, subscriber: String) : Future[Option[Subscription]]

  def getSubscriptions(transactionId: String): Future[Seq[Subscription]]

  def getSubscriptionsByRegime(regime: String, max: Int = 20): Future[Seq[Subscription]]

  def getSubscriptionStats(): Future[Map[String, Int]]

  def wipeTestData(): Future[WriteResult]
}

sealed trait SubscriptionStatus
case class SuccessfulSub(forced: Boolean = false) extends SubscriptionStatus
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
    collection.update(false).one(selector, sub, upsert = true) map {
      res =>
        UpsertResult(res.nModified, res.upserted.size, res.writeErrors)
    }
  }

  def deleteSub(transactionId: String, regime: String, subscriber: String): Future[WriteResult] = {
    val selector = BSONDocument("transactionId" -> transactionId, "regime" -> regime, "subscriber" -> subscriber)
     collection.delete().one(selector)
  }

  def getSubscription(transactionId: String, regime: String, subscriber: String): Future[Option[Subscription]] = {
    val query = BSONDocument("transactionId" -> transactionId, "regime" -> regime, "subscriber" -> subscriber)
    collection.find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites).one[Subscription]
  }

  def getSubscriptions(transactionId: String): Future[Seq[Subscription]] = {
    val query = BSONDocument("transactionId" -> transactionId)
    collection
      .find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[Subscription]()
      .collect[Seq](-1, Cursor.DoneOnError())
  }

  def getSubscriptionsByRegime(regime: String, max: Int): Future[Seq[Subscription]] = {
    val query = BSONDocument("regime" -> regime)
    collection
      .find(query, Option.empty)(BSONDocumentWrites, BSONDocumentWrites)
      .cursor[Subscription]().collect[Seq](maxDocs = max,Cursor.DoneOnError())
  }
  def getSubscriptionStats(): Future[Map[String, Int]] = {

    // needed to make it pick up the index
        val matchQuery: collection.PipelineOperator = collection.BatchCommands.AggregationFramework.Match(Json.obj("regime" -> Json.obj("$ne" -> "")))
    val project = collection.BatchCommands.AggregationFramework.Project(Json.obj("regime" -> 1, "_id" -> 0))
    // calculate the regime counts
    val group = collection.BatchCommands.AggregationFramework.Group(JsString("$regime"))("count" -> collection.BatchCommands.AggregationFramework.SumValue(1))

    val query = collection.aggregateWith[JsObject]()(_ => (matchQuery, List(project, group)))
    val fList =  query.collect(Int.MaxValue, Cursor.FailOnError[List[JsObject]]())
    fList.map{ _.map {
            documentWithRegimeAndCount =>{
              val regime = (documentWithRegimeAndCount \ "_id").as[String]
              val count = (documentWithRegimeAndCount \ "count").as[Int]
              regime -> count
            }
          }.toMap
        }
  }

  def wipeTestData(): Future[WriteResult] = {
    removeAll(reactivemongo.api.WriteConcern.Acknowledged)
  }
}