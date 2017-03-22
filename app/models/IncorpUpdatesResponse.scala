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
import play.api.libs.json.{Reads, _}
import play.api.libs.functional.syntax._

case class IncorpUpdatesResponse(
                                    items: Seq[IncorpUpdate],
                                    nextLink: String
                                  )
object IncorpUpdatesResponse {
  val dateReads = Reads[DateTime]( js =>
    js.validate[String].map[DateTime](
      DateTime.parse(_, DateTimeFormat.forPattern("yyyy-MM-dd"))
    )
  )


  implicit val reads : Reads[IncorpUpdatesResponse] = (
    ( __ \ "items" ).read[Seq[IncorpUpdate]] and
      (__ \ "links" \ "next").read[String]
    )(IncorpUpdatesResponse.apply _)

}

