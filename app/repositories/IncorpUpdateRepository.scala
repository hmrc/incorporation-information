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
import models.IncorpUpdate
import org.apache.commons.lang3.StringUtils
import play.api.Logger
import play.api.libs.json.Format
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.api.commands.{UpdateWriteResult, WriteError}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class IncorpUpdateMongoImpl @Inject()(val mongo: ReactiveMongoComponent) extends IncorpUpdateMongo
trait IncorpUpdateMongo extends ReactiveMongoFormats {
  val mongo:ReactiveMongoComponent
lazy val repo = new IncorpUpdateMongoRepository(mongo.mongoConnector.db, IncorpUpdate.mongoFormat)
}

trait IncorpUpdateRepository {
  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult]
  def storeSingleIncorpUpdate(updates: IncorpUpdate): Future[UpdateWriteResult]

  def getIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]]
}

class IncorpUpdateMongoRepository(mongo: () => DB, format: Format[IncorpUpdate]) extends ReactiveRepository[IncorpUpdate, BSONObjectID](
  collectionName = "incorporation-information",
  mongo = mongo,
  domainFormat = format
) with IncorpUpdateRepository
  with MongoErrorCodes {

  implicit val fmt = format

  private def selector(transactionId: String) = BSONDocument("_id" -> transactionId)

  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    bulkInsert(updates) map {
      wr =>
        val nonDupIU = nonDuplicateIncorporations(updates, wr.writeErrors)
        val inserted = wr.n
        val (duplicates, errors) = wr.writeErrors.partition(_.code == ERR_DUPLICATE)
        InsertResult(inserted, duplicates.size, errors, insertedItems = nonDupIU)
    }
  }

  def storeSingleIncorpUpdate(iUpdate: IncorpUpdate): Future[UpdateWriteResult] = {
    implicit val mongoFormat = IncorpUpdate.mongoFormat
    collection.update(false).one(selector(iUpdate.transactionId), iUpdate, upsert = true)
  }

  private[repositories] def nonDuplicateIncorporations(updates: Seq[IncorpUpdate], errs: Seq[WriteError]): Seq[IncorpUpdate] = {
    val duplicates = errs collect {
      case WriteError(_, ERR_DUPLICATE, msg) => StringUtils.substringBetween(msg, "\"", "\"")
    }

    val uniques = (updates.map(_.transactionId) diff duplicates).toSet
    uniques foreach (u => Logger.info(s"[UniqueIncorp] transactionId : $u"))

    updates.filter( i => uniques.contains(i.transactionId) )
  }

  def getIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]] = {
    collection.find(selector(transactionId), Option.empty)(BSONDocumentWrites, BSONDocumentWrites).one[IncorpUpdate]
  }
}

case class InsertResult(inserted: Int,
                         duplicate: Int,
                         errors: Seq[WriteError] = Seq(),
                         alerts: Int = 0,
                         insertedItems: Seq[IncorpUpdate] = Seq()
                       )
