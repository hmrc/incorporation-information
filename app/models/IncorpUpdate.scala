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

package models

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import utils.TimestampFormats._

import scala.language.implicitConversions

case class IncorpUpdate(transactionId : String,
                        status : String,
                        crn : Option[String],
                        incorpDate:  Option[DateTime],
                        timepoint : String,
                        statusDescription : Option[String] = None)

object IncorpUpdate {
  implicit val format: OFormat[IncorpUpdate] = Json.format[IncorpUpdate]

  val mongoFormat: OFormat[IncorpUpdate] = (
    (__ \ "_id").format[String] and
      (__ \ "transaction_status").format[String] and
      (__ \ "company_number").formatNullable[String] and
      (__ \ "incorporated_on").formatNullable[DateTime](dateFormat) and
      (__ \ "timepoint").format[String] and
      (__ \ "transaction_status_description").formatNullable[String]
    ) (IncorpUpdate.apply, unlift(IncorpUpdate.unapply))

  val cohoFormat: OFormat[IncorpUpdate] = (
    (__ \ "transaction_id").format[String] and
      (__ \ "transaction_status").format[String] and
      (__ \ "company_number").formatNullable[String] and
      (__ \ "incorporated_on").formatNullable[DateTime](dateFormat) and
      (__ \ "timepoint").format[String] and
      (__ \ "transaction_status_description").formatNullable[String]
    ) (IncorpUpdate.apply, unlift(IncorpUpdate.unapply))

  val responseFormat: OFormat[IncorpUpdate] = (
    (__ \ "transaction_id").format[String] and
      (__ \ "status").format[String] and
      (__ \ "crn").formatNullable[String] and
      (__ \ "incorporationDate").formatNullable[DateTime](dateFormat) and
      (__ \ "timepoint").format[String] and
      (__ \ "transaction_status_description").formatNullable[String]
    ) (IncorpUpdate.apply, unlift(IncorpUpdate.unapply))
}

case class IncorpStatusEvent(status: String, crn: Option[String], incorporationDate: Option[DateTime], description: Option[String], timestamp: DateTime)

object IncorpStatusEvent {
  val writes: OFormat[IncorpStatusEvent] = (
    (__ \ "status").format[String] and
      (__ \ "crn").formatNullable[String] and
      (__ \ "incorporationDate").formatNullable[DateTime] and
      (__ \ "description").formatNullable[String] and
      (__ \ "timestamp").format[DateTime]
    ) (IncorpStatusEvent.apply, unlift(IncorpStatusEvent.unapply))

    implicit val format: OFormat[IncorpStatusEvent] = Json.format[IncorpStatusEvent]
}

case class IncorpUpdateResponse(regime: String, subscriber: String, callbackUrl: String, incorpUpdate: IncorpUpdate)

object IncorpUpdateResponse {

  def writes: Writes[IncorpUpdateResponse] = new Writes[IncorpUpdateResponse] {

    def writes(u: IncorpUpdateResponse): JsValue = {
      Json.obj(
        "SCRSIncorpStatus" -> Json.obj(
          "IncorpSubscriptionKey" -> Json.obj(
            "subscriber" -> u.subscriber,
            "discriminator" -> u.regime,
            "transactionId" -> u.incorpUpdate.transactionId
          ),
          "SCRSIncorpSubscription" -> Json.obj(
            "callbackUrl" -> u.callbackUrl
          ),
          "IncorpStatusEvent" -> Json.toJson(toIncorpStatusEvent(u.incorpUpdate))(IncorpStatusEvent.writes).as[JsObject]
        )
      )
    }

    private def toIncorpStatusEvent(u: IncorpUpdate): IncorpStatusEvent = {
      IncorpStatusEvent(u.status, u.crn, u.incorpDate, u.statusDescription, now)
    }

    def now: DateTime = DateTime.now(DateTimeZone.UTC)
  }

  implicit val format: OFormat[IncorpUpdateResponse] = Json.format[IncorpUpdateResponse]
}

case class QueuedIncorpUpdate(timestamp: DateTime, incorpUpdate: IncorpUpdate)

object QueuedIncorpUpdate {
  val format: OFormat[QueuedIncorpUpdate] = (
      (__ \ "timestamp").format[DateTime] and
      (__ \ "incorp_update").format[IncorpUpdate](IncorpUpdate.cohoFormat)
  ) (QueuedIncorpUpdate.apply, unlift(QueuedIncorpUpdate.unapply))
}