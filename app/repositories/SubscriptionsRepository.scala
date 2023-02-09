/*
 * Copyright 2023 HM Revenue & Customs
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


import models.{IncorpUpdate, Subscription}
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model._
import org.mongodb.scala.result.DeleteResult
import org.mongodb.scala.{MongoWriteException, WriteError}
import play.api.libs.json.{Format, JsObject}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionsMongo @Inject()(mongo: MongoComponent)(implicit val ec: ExecutionContext) {
  lazy val repo = new SubscriptionsMongoRepository(mongo)
}

trait SubscriptionsRepository {
  implicit val ec: ExecutionContext

  def insertSub(sub: Subscription): Future[UpsertResult]

  def deleteSub(transactionId: String, regime: String, subscriber: String): Future[DeleteResult]

  def getSubscription(transactionId: String, regime: String, subscriber: String) : Future[Option[Subscription]]

  def getSubscriptions(transactionId: String): Future[Seq[Subscription]]

  def getSubscriptionsByRegime(regime: String, max: Int = 20): Future[Seq[Subscription]]

  def getSubscriptionStats(): Future[Map[String, Int]]
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

class SubscriptionsMongoRepository(mongo: MongoComponent)(implicit val ec: ExecutionContext) extends PlayMongoRepository[Subscription](
  mongoComponent = mongo,
  collectionName = "subscriptions",
  domainFormat = Subscription.format,
  indexes = Seq(
    IndexModel(
      ascending("transactionId", "regime", "subscriber"),
      IndexOptions()
        .name("SubscriptionIdIndex")
        .unique(true)
        .sparse(false)
    ),
    IndexModel(
      ascending("regime"),
      IndexOptions()
        .name("regime")
        .unique(false)
    )
  ),
  extraCodecs = Seq(Codecs.playFormatCodec[JsObject](implicitly[Format[JsObject]]))
) with SubscriptionsRepository {

  def selector(transactionId: String, regime: String, subscriber: String) =
    Filters.and(equal("transactionId", transactionId), equal("regime", regime), equal("subscriber", subscriber))

  def insertSub(sub: Subscription) : Future[UpsertResult] =
    collection.replaceOne(selector(sub.transactionId, sub.regime, sub.subscriber), sub, ReplaceOptions().upsert(true)).toFuture() map {
      res =>
        val wasInserted = Option(res.getUpsertedId).isDefined
        UpsertResult(if(wasInserted) 0 else 1, if(wasInserted) 1 else 0, Seq())
    } recover {
      case ex: MongoWriteException =>
        UpsertResult(0, 0, Seq(ex.getError))
    }

  def deleteSub(transactionId: String, regime: String, subscriber: String): Future[DeleteResult] =
    collection.deleteOne(selector(transactionId, regime, subscriber)).toFuture()

  def getSubscription(transactionId: String, regime: String, subscriber: String): Future[Option[Subscription]] =
    collection.find(selector(transactionId, regime, subscriber)).headOption()

  def getSubscriptions(transactionId: String): Future[Seq[Subscription]] =
    collection.find(equal("transactionId", transactionId)).toFuture()

  def getSubscriptionsByRegime(regime: String, max: Int): Future[Seq[Subscription]] =
    collection.find(equal("regime", regime)).limit(max).toFuture()

  def getSubscriptionStats(): Future[Map[String, Int]] = {

    // needed to make it pick up the index
    val matchQuery: Bson = Aggregates.`match`(Filters.ne("regime", ""))
    val project = Aggregates.project(BsonDocument("regime" -> 1, "_id" -> 0))
    // calculate the regime counts
    val group = Aggregates.group("$regime", BsonField("count", BsonDocument("$sum" -> 1)))

    val fList = collection.aggregate[JsObject](Seq(matchQuery, project, group)).toFuture()
    fList.map{ _.map {
      documentWithRegimeAndCount =>{
        val regime = (documentWithRegimeAndCount \ "_id").as[String]
        val count = (documentWithRegimeAndCount \ "count").as[Int]
        regime -> count
      }
    }.toMap
    }
  }
}