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

import com.mongodb.WriteError
import com.mongodb.bulk.BulkWriteError
import models.IncorpUpdate
import org.apache.commons.lang3.StringUtils
import org.mongodb.scala.MongoBulkWriteException
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{BulkWriteOptions, InsertOneModel, ReplaceOptions}
import org.mongodb.scala.result.UpdateResult
import utils.Logging
import play.api.libs.json.Format
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


class IncorpUpdateMongoImpl @Inject()(val mongo: MongoComponent)(implicit val ec: ExecutionContext) extends IncorpUpdateMongo

trait IncorpUpdateMongo {
  implicit val ec: ExecutionContext
  val mongo: MongoComponent
  lazy val repo = new IncorpUpdateMongoRepository(mongo, IncorpUpdate.mongoFormat)
}

trait IncorpUpdateRepository {
  implicit val ec: ExecutionContext

  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult]

  def storeSingleIncorpUpdate(updates: IncorpUpdate): Future[UpdateResult]

  def getIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]]
}

class IncorpUpdateMongoRepository(val mongo: MongoComponent, format: Format[IncorpUpdate])
                                 (implicit val ec: ExecutionContext) extends PlayMongoRepository[IncorpUpdate](
  mongoComponent = mongo,
  collectionName = "incorporation-information",
  domainFormat = format,
  indexes = Seq()
) with IncorpUpdateRepository with MongoErrorCodes with Logging {

  implicit val fmt = format

  private def selector(transactionId: String) = equal("_id", transactionId)

  def storeIncorpUpdates(updates: Seq[IncorpUpdate]): Future[InsertResult] = {
    if(updates.nonEmpty) {
      collection.bulkWrite(updates.map(InsertOneModel(_)), BulkWriteOptions().ordered(false)).toFuture.map { result =>
        InsertResult(inserted = result.getInsertedCount, duplicate = 0, insertedItems = updates)
      } recover {
        case ex: MongoBulkWriteException =>
          val nonDupIU = nonDuplicateIncorporations(updates, ex.getWriteErrors.asScala)
          val inserted = ex.getWriteResult.getInsertedCount
          val (duplicates, errors) = ex.getWriteErrors.asScala.partition(_.getCode == ERR_DUPLICATE)
          InsertResult(inserted, duplicates.size, errors, insertedItems = nonDupIU)
      }
    } else {
      Future.successful(InsertResult(0, 0, Seq()))
    }
  }

  def storeSingleIncorpUpdate(iUpdate: IncorpUpdate): Future[UpdateResult] =
    collection.replaceOne(
      filter = selector(iUpdate.transactionId),
      replacement = iUpdate,
      options = ReplaceOptions().upsert(true)
    ).toFuture()

  def getIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]] =
    collection.find(selector(transactionId)).headOption()

  private[repositories] def nonDuplicateIncorporations(updates: Seq[IncorpUpdate], errs: Seq[BulkWriteError]): Seq[IncorpUpdate] = {
    val duplicates = errs collect {
      case err: WriteError if err.getCode == ERR_DUPLICATE => StringUtils.substringBetween(err.getMessage, "\"", "\"")
    }

    val uniques = (updates.map(_.transactionId) diff duplicates).toSet
    uniques foreach (u => logger.info(s"[UniqueIncorp] transactionId : $u"))

    updates.filter(i => uniques.contains(i.transactionId))
  }
}

case class InsertResult(inserted: Int,
                        duplicate: Int,
                        errors: Seq[WriteError] = Seq(),
                        alerts: Int = 0,
                        insertedItems: Seq[IncorpUpdate] = Seq())
