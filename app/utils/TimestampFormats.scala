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

package utils

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import play.api.libs.json.JodaReads.DefaultJodaDateTimeReads
import play.api.libs.json.JodaWrites.JodaDateTimeNumberWrites
import play.api.libs.json.{Format, JsString, Reads, Writes}

object TimestampFormats {

  val datePattern = "yyyy-MM-dd"

  val dateFormat = Format[DateTime](
    Reads[DateTime](js =>
      js.validate[String].map {
        date => DateTime.parse(date, DateTimeFormat.forPattern(datePattern))
    }
    ),
    Writes[DateTime](d =>
      JsString(ISODateTimeFormat.date().print(d))
    )
  )

  implicit val jodaDateTimeFormat: Format[DateTime] = Format(DefaultJodaDateTimeReads, JodaDateTimeNumberWrites)

}

