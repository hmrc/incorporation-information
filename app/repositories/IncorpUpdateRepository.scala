/*
* Copyright 2016 HM Revenue & Customs
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

import models.IncorpUpdate
import play.api.libs.json.Format
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.api.commands.WriteError
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class IncorpUpdateMongo @Inject()(mongo: ReactiveMongoComponent) extends ReactiveMongoFormats {
  val repo = new IncorpUpdateMongoRepository(mongo.mongoConnector.db, IncorpUpdate.format)
}

trait IncorpUpdateRepository {
  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult]
}

class IncorpUpdateMongoRepository(mongo: () => DB, format: Format[IncorpUpdate]) extends ReactiveRepository[IncorpUpdate, BSONObjectID](
  collectionName = "incorporation-information",
  mongo = mongo,
  domainFormat = format
) with IncorpUpdateRepository
  with MongoErrorCodes {

  implicit val fmt = format

  private def selector(update: IncorpUpdate) = BSONDocument("_id" -> update.transactionId)

  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    bulkInsert(updates) map {
      wr =>
        val inserted = wr.n
        val (duplicates, errors) = wr.writeErrors.partition(e => e.code == ERR_DUPLICATE)
        InsertResult(inserted, duplicates.size, errors)
    }
  }
}

case class InsertResult(inserted: Int, duplicate: Int, errors: Seq[WriteError])
