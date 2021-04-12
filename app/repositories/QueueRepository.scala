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
import models.QueuedIncorpUpdate
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.{Format, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, DB, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.core.errors.DatabaseException
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class QueueMongoImpl @Inject()(val mongo: ReactiveMongoComponent) extends QueueMongo

  trait QueueMongo extends ReactiveMongoFormats {
    val mongo: ReactiveMongoComponent
    lazy val repo = new QueueMongoRepository(mongo.mongoConnector.db, QueuedIncorpUpdate.format)
}

trait QueueRepository {

  def storeIncorpUpdates(updates: Seq[QueuedIncorpUpdate]): Future[InsertResult]

  def upsertIncorpUpdate(update: QueuedIncorpUpdate): Future[InsertResult]

  def getIncorpUpdate(transactionId: String): Future[Option[QueuedIncorpUpdate]]

  def getIncorpUpdates(fetchSize: Int): Future[Seq[QueuedIncorpUpdate]]

  def removeQueuedIncorpUpdate(transactionId: String): Future[Boolean]

  def updateTimestamp(transactionId: String, newTS: DateTime): Future[Boolean]
}

class QueueMongoRepository(mongo: () => DB, format: Format[QueuedIncorpUpdate]) extends ReactiveRepository[QueuedIncorpUpdate, BSONObjectID](
  collectionName = "incorp-update-queue",
  mongo = mongo,
  domainFormat = format
) with QueueRepository
  with MongoErrorCodes {

  implicit val fmt = format

  private def txSelector(transactionId: String) = BSONDocument("incorp_update.transaction_id" -> transactionId)

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("incorp_update.transaction_id" -> IndexType.Ascending, "timestamp" -> IndexType.Ascending),
      name = Some("QueuedIncorpIndex"), unique = true, sparse = false
    ),
    Index(
      key = Seq("timestamp" -> IndexType.Ascending), name = Some("QueueByTs"), unique = false
    )
  )

  override def storeIncorpUpdates(updates: Seq[QueuedIncorpUpdate]): Future[InsertResult] = {
    bulkInsert(updates) map {
      wr =>
        val inserted = wr.n
        val (duplicates, errors) = wr.writeErrors.partition(_.code == ERR_DUPLICATE)
        InsertResult(inserted, duplicates.size, errors)
    } recover {
      case ex: DatabaseException =>
        Logger.info(s"Failed to store incorp update with transactionId: ${ex.originalDocument.get.get("incorp_update.transaction_id").get.toString} due to error: $ex")
        throw new Exception
    }
  }

  override def upsertIncorpUpdate(update: QueuedIncorpUpdate): Future[InsertResult] = {
    val selector = txSelector(update.incorpUpdate.transactionId)
    implicit val formatter = QueuedIncorpUpdate.format
    collection.update(false).one(selector, update, upsert = true) map (res => InsertResult(res.nModified, res.upserted.size, res.writeErrors))
  }

  override def getIncorpUpdate(transactionId: String): Future[Option[QueuedIncorpUpdate]] = {
    collection.find(txSelector(transactionId),Option.empty)(BSONDocumentWrites,BSONDocumentWrites).one[QueuedIncorpUpdate]
  }

  override def getIncorpUpdates(fetchSize: Int): Future[Seq[QueuedIncorpUpdate]] = {
    val selector = Json.obj("timestamp" -> Json.obj("$lte" -> DateTime.now.getMillis))
    val rp = ReadPreference.primaryPreferred

    collection
      .find(selector, Option.empty)(JsObjectDocumentWriter, JsObjectDocumentWriter)
      .sort(Json.obj("timestamp" -> 1))
      .cursor[QueuedIncorpUpdate](rp)
      .collect[List](maxDocs = fetchSize, Cursor.FailOnError())
  }

  override def removeQueuedIncorpUpdate(transactionId: String): Future[Boolean] = {
    collection.delete().one(txSelector(transactionId)).map(_.n > 0)
  }

  override def updateTimestamp(transactionId: String, newTS: DateTime): Future[Boolean] = {
   val ts = newTS.getMillis
    val modifier = BSONDocument("$set" -> BSONDocument("timestamp" -> ts))
    collection.findAndUpdate(txSelector(transactionId), modifier, true, false).map {
      res => res.value.fold(false){
        _.value("timestamp").validate[Long]
          .fold(_ => {
            Logger.error("updateTimestamp could not be converted to a long")
            false
          }, tsFromUpdate =>
            if(tsFromUpdate == ts) {
              true
            } else {
              Logger.info(s"updateTimestamp did not return the correct timestamp that was inserted on doc: $tsFromUpdate inserted: $ts")
              false
            }
        )}
    }
  }
}
