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

import constants.CollectionNames._
import models.QueuedIncorpUpdate
import play.api.libs.json.Format
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.api.indexes.{IndexType, Index}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class QueueMongo @Inject()(mongo: ReactiveMongoComponent) extends ReactiveMongoFormats {
  val repo = new QueueMongoRepository(mongo.mongoConnector.db, QueuedIncorpUpdate.format)
}

trait QueueRepository {
  def storeIncorpUpdates(updates: Seq[QueuedIncorpUpdate]): Future[InsertResult]

  def getIncorpUpdate(transactionId: String): Future[Option[QueuedIncorpUpdate]]

  def removeQueuedIncorpUpdate(transactionId: String): Future[Boolean]
}

class QueueMongoRepository(mongo: () => DB, format: Format[QueuedIncorpUpdate]) extends ReactiveRepository[QueuedIncorpUpdate, BSONObjectID](
  collectionName = QUEUE,
  mongo = mongo,
  domainFormat = format
) with QueueRepository
  with MongoErrorCodes {

  implicit val fmt = format

  private def selector(transactionId: String) = BSONDocument("incorp_update.transaction_id" -> transactionId)

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("incorp_update.transaction_id" -> IndexType.Ascending),
      name = Some("QueuedIncorpIndex"), unique = true, sparse = false
    )
  )

  override def storeIncorpUpdates(updates: Seq[QueuedIncorpUpdate]): Future[InsertResult] = {
    bulkInsert(updates) map {
      wr =>
        val inserted = wr.n
        val (duplicates, errors) = wr.writeErrors.partition(_.code == ERR_DUPLICATE)
        InsertResult(inserted, duplicates.size, errors)
    }
  }

  override def getIncorpUpdate(transactionId: String): Future[Option[QueuedIncorpUpdate]] = {
    collection.find(selector(transactionId)).one[QueuedIncorpUpdate]
  }

  override def removeQueuedIncorpUpdate(transactionId: String): Future[Boolean] = {
    collection.remove(selector(transactionId)).map(_.n > 0)
  }
}