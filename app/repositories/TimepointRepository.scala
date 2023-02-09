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


import com.mongodb.client.model.Updates.set
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{FindOneAndUpdateOptions, ReturnDocument}
import utils.Logging
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

case class TimePoint(
                      _id: String,
                      timepoint: String
                    )

object TimePoint {
  implicit val formats = Json.format[TimePoint]
}

class TimepointMongo @Inject()(mongo: MongoComponent) {
  implicit val ec: ExecutionContext = global
  lazy val repo = new TimepointMongoRepository(mongo)
}

trait TimepointRepository {
  implicit val ec: ExecutionContext

  def updateTimepoint(s: String): Future[String]

  def retrieveTimePoint: Future[Option[String]]
}

class TimepointMongoRepository(mongo: MongoComponent)
                              (implicit val ec: ExecutionContext)
  extends PlayMongoRepository[TimePoint](
    mongoComponent = mongo,
    collectionName = "time-points",
    domainFormat = TimePoint.formats,
    indexes = Seq()
  ) with TimepointRepository with Logging {

  private val selector = equal("_id", "CH-INCORPSTATUS-TIMEPOINT")

  def updateTimepoint(timePoint: String): Future[String] =
    collection.findOneAndUpdate(
      selector,
      set("timepoint", timePoint),
      FindOneAndUpdateOptions()
        .upsert(true)
        .returnDocument(ReturnDocument.AFTER)
    ).toFuture().map(_.timepoint)

  def retrieveTimePoint: Future[Option[String]] = {
    collection.find(selector).headOption() map {
      case Some(res) => Some(res.timepoint)
      case _ =>
        logger.warn("[retrieveTimePoint] Could not find an existing Timepoint - this is ok for first run of the system")
        None
    }
  }
}
