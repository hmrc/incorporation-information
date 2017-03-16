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

import play.api.libs.json.JsObject
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.api.DB
import reactivemongo.api.commands.{MultiBulkWriteResult, UpdateWriteResult, WriteError, WriteResult}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class IncorpUpdate(txId: String, crn: String)

object IncorpUpdate {
  val formats = (
      (__ \ "_id").format[String] and
      (__ \ "crn").format[String]
    ) (IncorpUpdate.apply _, unlift(IncorpUpdate.unapply))
}

trait BulkInsertRepo extends Repository[IncorpUpdate, BSONObjectID] {
  def storeUpdatesOneByOne(updates: Seq[IncorpUpdate]): Future[InsertResult]
  def storeUpdatesAll(updates: Seq[IncorpUpdate]): Future[InsertResult]
}

trait MongoErrorCodes {
  val ERR_DUPLICATE = 11000
  val ERR_INVALID = 121
}

case class InsertResult(inserted: Int, duplicate: Int, errors: Seq[WriteError])

class BulkInsertMongoRepo(implicit mongo: () => DB)
  extends ReactiveRepository[IncorpUpdate, BSONObjectID]("incorp-info", mongo, IncorpUpdate.formats, ReactiveMongoFormats.objectIdFormats)
    with BulkInsertRepo with MongoErrorCodes {

  // no extra indexes YET!

  implicit val updateFormatter = IncorpUpdate.formats

  private def selector(update: IncorpUpdate) = BSONDocument("_id" -> update.txId)

  def storeUpdatesOneByOne(updates: Seq[IncorpUpdate]): Future[InsertResult] = {

    val fUpdates = updates.map {
      update =>
        collection.update(selector(update), update, upsert = true)
    }

    val fResults = fUpdates.map(
      f => f.map( wr => wr ).recover{ case e : UpdateWriteResult => e }
    )

    Future.sequence( fResults ).map { s =>
     s.foldLeft[InsertResult](InsertResult(0, 0, Seq())) { (prev, wr) =>
       val upserted = wr.upserted.size
       val inserted = prev.inserted + upserted
       val duplicate = prev.duplicate + (wr.n - upserted)
       // TODO the index in the error isn't correct
       val errors = prev.errors ++ wr.writeErrors
       InsertResult(inserted, duplicate, errors)
      }
    }
  }

  def storeUpdatesAll(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    bulkInsert(updates) map {
      wr =>
        val inserted = wr.n
        val (duplicates, errors) = wr.writeErrors.partition(e => e.code == ERR_DUPLICATE)
        InsertResult(inserted, duplicates.size, errors)
    }
 }

}
