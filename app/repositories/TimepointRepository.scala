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

import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class TimePoint(
                      _id : String,
                      timepoint: String
                    )


object TimePoint {
  implicit val formats = Json.format[TimePoint]
}

@Singleton
class TimepointMongo @Inject()(mongo: ReactiveMongoComponent) extends ReactiveMongoFormats {
  val repo = new TimepointMongoRepository(mongo.mongoConnector.db)
}

trait TimepointRepository extends Repository[TimePoint, BSONObjectID] {
  def updateTimepoint(s: String) : Future[String]
  def retrieveTimePoint : Future[Option[String]]
}

class TimepointMongoRepository(mongo: () => DB)
  extends ReactiveRepository[TimePoint, BSONObjectID]("state-data", mongo, TimePoint.formats, ReactiveMongoFormats.objectIdFormats)
    with TimepointRepository {

  private val selector = BSONDocument("_id" -> "CH-INCORPSTATUS-TIMEPOINT")

  def updateTimepoint(timePoint: String) = {
    val modifier = BSONDocument("$set" -> BSONDocument("timepoint" -> timePoint))

    collection.findAndUpdate(selector, modifier, fetchNewObject = true, upsert = true) map { r =>
      (r.result[JsValue].get \ "timepoint").as[String]
    }
  }

  def retrieveTimePoint = {
    collection.find(selector).one[TimePoint] map {
      case Some(res) => Some(res.timepoint)
      case _ =>
        Logger.warn("Could not find an existing Timepoint - this is ok for first run of the system")
        None
    }
  }

}