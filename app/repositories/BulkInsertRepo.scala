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
import reactivemongo.api.commands.{MultiBulkWriteResult, WriteResult}
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
  def storeUpdatesOneByOne(updates: Seq[IncorpUpdate]): Future[Int]
  def storeUpdatesAll(updates: Seq[IncorpUpdate]): Future[MultiBulkWriteResult]
}

class BulkInsertMongoRepo(implicit mongo: () => DB)
  extends ReactiveRepository[IncorpUpdate, BSONObjectID]("incorp-info", mongo, IncorpUpdate.formats, ReactiveMongoFormats.objectIdFormats)
    with BulkInsertRepo {

  // no extra indexes YET!

  implicit val updateFormatter = IncorpUpdate.formats

  private def selector(update: IncorpUpdate) = BSONDocument("_id" -> update.txId)

  def storeUpdatesOneByOne(updates: Seq[IncorpUpdate]): Future[Int] = {
    val fUpdates = updates.map {
      update =>
        collection.update(selector(update), update, upsert = true)
    }

    Future.fold(fUpdates)(0){
      (n, wr) =>
        n + wr.n
    }
  }

  def storeUpdatesAll(updates: Seq[IncorpUpdate]): Future[MultiBulkWriteResult] = {

    bulkInsert(updates) map {
      wr =>
        wr
    }
 }

}
