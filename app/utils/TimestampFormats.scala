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

package utils

import play.api.libs.json._

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField
import java.time.{Instant, LocalDateTime, ZoneOffset}

object TimestampFormats {

  val ldtFormatter: DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern("uuuu-MM-dd['T'HH:mm:ss]")
      .optionalStart()
      .appendFraction(ChronoField.MICRO_OF_SECOND, 1, 5, true)
      .optionalEnd()
      .optionalStart()
      .appendZoneId()
      .optionalEnd()
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
      .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
      .parseDefaulting(ChronoField.MICRO_OF_SECOND, 0)
      .toFormatter()

  val dateFormat = Format[LocalDateTime](
    Reads[LocalDateTime](js =>
      js.validate[String].map {
        date => LocalDateTime.parse(date, ldtFormatter)
      }
    ),
    Writes[LocalDateTime](d =>
      JsString(d.toLocalDate.toString)
    )
  )

  implicit val milliDateTimeFormat: Format[LocalDateTime] = Format[LocalDateTime](
    Reads[LocalDateTime](js =>
      js.validate[Long].map {
        epoch => Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC).toLocalDateTime
      }
    ),
    Writes[LocalDateTime](d =>
      JsNumber(d.toInstant(ZoneOffset.UTC).toEpochMilli)
    )
  )

}

