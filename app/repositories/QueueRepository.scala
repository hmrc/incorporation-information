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

package repositories

import com.mongodb.client.model.Updates.set
import models.QueuedIncorpUpdate
import org.joda.time.DateTime
import org.mongodb.scala.model.Filters.{equal, lte}
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoBulkWriteException, MongoWriteException}
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


class QueueMongoImpl @Inject()(val mongo: MongoComponent)(implicit val ec: ExecutionContext) extends QueueMongo

trait QueueMongo {
  implicit val ec: ExecutionContext
  val mongo: MongoComponent
  lazy val repo = new QueueMongoRepository(mongo, QueuedIncorpUpdate.format)
}

trait QueueRepository {
  implicit val ec: ExecutionContext

  def storeIncorpUpdates(updates: Seq[QueuedIncorpUpdate]): Future[InsertResult]

  def upsertIncorpUpdate(update: QueuedIncorpUpdate): Future[InsertResult]

  def getIncorpUpdate(transactionId: String): Future[Option[QueuedIncorpUpdate]]

  def getIncorpUpdates(fetchSize: Int): Future[Seq[QueuedIncorpUpdate]]

  def removeQueuedIncorpUpdate(transactionId: String): Future[Boolean]

  def updateTimestamp(transactionId: String, newTS: DateTime): Future[Boolean]
}

class QueueMongoRepository(mongo: MongoComponent, format: Format[QueuedIncorpUpdate])(implicit val ec: ExecutionContext) extends PlayMongoRepository[QueuedIncorpUpdate](
  mongoComponent = mongo,
  collectionName = "incorp-update-queue",
  domainFormat = format,
  indexes = Seq(
    IndexModel(
      ascending("incorp_update.transaction_id", "timestamp"),
      IndexOptions()
        .name("QueuedIncorpIndex")
        .unique(true)
        .sparse(false)
    ),
    IndexModel(
      ascending("timestamp"),
      IndexOptions()
        .name("QueueByTs")
        .unique(false)
    )
  )
) with QueueRepository
  with MongoErrorCodes {

  implicit val fmt = format

  private def txSelector(transactionId: String) = equal("incorp_update.transaction_id", transactionId)

  override def storeIncorpUpdates(updates: Seq[QueuedIncorpUpdate]): Future[InsertResult] = {
    if(updates.nonEmpty) {
      collection.bulkWrite(updates.map(InsertOneModel(_)), BulkWriteOptions().ordered(false)).toFuture().map { result =>
        InsertResult(inserted = result.getInsertedCount, duplicate = 0)
      } recover {
        case ex: MongoBulkWriteException =>
          val inserted = ex.getWriteResult.getInsertedCount
          val (duplicates, errors) = ex.getWriteErrors.asScala.partition(_.getCode == ERR_DUPLICATE)
          InsertResult(inserted, duplicates.size, errors)
      }
    } else {
      Future.successful(InsertResult(0, 0, Seq()))
    }
  }

  override def upsertIncorpUpdate(update: QueuedIncorpUpdate): Future[InsertResult] =
    collection.replaceOne(txSelector(update.incorpUpdate.transactionId), update, ReplaceOptions().upsert(true)).toFuture() map { result =>
      val wasInserted = Option(result.getUpsertedId).isDefined
      InsertResult(1, if(wasInserted) 1 else 0, Seq())
    } recover {
      case ex: MongoWriteException => InsertResult(0, 0, Seq(ex.getError))
    }

  override def getIncorpUpdate(transactionId: String): Future[Option[QueuedIncorpUpdate]] =
    collection.find(txSelector(transactionId)).headOption()

  override def getIncorpUpdates(fetchSize: Int): Future[Seq[QueuedIncorpUpdate]] =
    collection
      .find(lte("timestamp", DateTime.now.getMillis))
      .sort(Sorts.ascending("timestamp"))
      .limit(fetchSize)
      .toFuture()

  override def removeQueuedIncorpUpdate(transactionId: String): Future[Boolean] =
    collection.deleteOne(txSelector(transactionId)).toFuture().map(_.getDeletedCount > 0)

  override def updateTimestamp(transactionId: String, newTS: DateTime): Future[Boolean] =
    collection.updateOne(txSelector(transactionId), set("timestamp", newTS.getMillis)).toFuture().map(_.getModifiedCount > 0)
}
