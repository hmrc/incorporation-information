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

package models

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class IncorpUpdate(transactionId : String,
                        status : String,
                        crn : Option[String],
                        incorpDate:  Option[DateTime],
                        timepoint : String,
                        statusDescription : Option[String] = None)

object IncorpUpdate {
  private val dateReads = Reads[DateTime]( js =>
    js.validate[String].map[DateTime](
      DateTime.parse(_, DateTimeFormat.forPattern("yyyy-MM-dd"))
    )
  )

  val mongoFormat = (
    ( __ \ "_id" ).format[String] and
      ( __ \ "transaction_status" ).format[String] and
      ( __ \ "company_number" ).formatNullable[String] and
      ( __ \ "incorporated_on" ).formatNullable[DateTime] and
      ( __ \ "timepoint" ).format[String] and
      ( __ \ "transaction_status_description" ).formatNullable[String]
    )(IncorpUpdate.apply, unlift(IncorpUpdate.unapply))


  val apiFormat = (
    ( __ \ "transaction_id" ).format[String] and
      ( __ \ "transaction_status" ).format[String] and
      ( __ \ "company_number" ).formatNullable[String] and
      ( __ \ "incorporated_on" ).formatNullable[DateTime] and
      ( __ \ "timepoint" ).format[String] and
      ( __ \ "transaction_status_description" ).formatNullable[String]
    )(IncorpUpdate.apply, unlift(IncorpUpdate.unapply))


  def writes(callBackUrl: String, transactionId: String): Writes[IncorpUpdate] = new Writes[IncorpUpdate] {

    def writes(u: IncorpUpdate) = {
      Json.obj(
        "SCRSIncorpStatus" -> Json.obj(
          "IncorpSubscriptionKey" -> Json.obj(
            "subscriber" -> "SCRS",
            "discriminator" -> "PAYE",
            "transactionId" -> transactionId
          ),
          "SCRSIncorpSubscription" -> Json.obj(
            "callbackUrl" -> callBackUrl
          ),
            "IncorpStatusEvent" -> Json.obj(
              "status" -> u.status,
              "timestamp" -> "2017-12-21T10:13:09.429Z"//todo: create timestamp here?
            ).++(
              u.statusDescription.fold[JsObject](Json.obj())(s => Json.obj("description" -> s))
            ).++(
              u.crn.fold[JsObject](Json.obj())(s => Json.obj("crn" -> s))
            ).++(
              u.incorpDate.fold[JsObject](Json.obj())(s => Json.obj("incorporationDate" -> s))
            )
          )
      )
    }
  }
}
