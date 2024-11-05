/*
 * Copyright 2024 HM Revenue & Customs
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

import Helpers.SCRSSpec
import org.scalatest.BeforeAndAfterEach

import java.time.{LocalDateTime, ZoneId}

class TimestampFormatsSpec extends SCRSSpec with BeforeAndAfterEach {

  "ldtFormatter" must {

    "parse a string with no timestamp to a LocalDateTime with default startOfDay" in {
      LocalDateTime.parse("2020-01-01", TimestampFormats.ldtFormatter) mustBe
        LocalDateTime.of(2020, 1, 1, 0, 0, 0, 0)
    }

    Seq(None, Some("UTC"), Some("Europe/London"), Some("GMT+2")).foreach { optTimeZone =>

      s"when timezone is '$optTimeZone'" must {

        def parseDate(millis: String = "") =
          LocalDateTime.parse("2020-01-01T01:22:15" + millis + optTimeZone.fold("")(ZoneId.of(_).getId), TimestampFormats.ldtFormatter)

        def expectedLocalDateTime(millis: Int = 0) =
          LocalDateTime.of(2020, 1, 1, 1, 22, 15, "%-9s".format(millis).replace(" ", "0").toInt)

        "parse a string with timestamp in seconds" in {
          parseDate() mustBe expectedLocalDateTime()
        }

        "parse a string with timestamp with single precision fraction of a second" in {
          parseDate(".1") mustBe expectedLocalDateTime(1)
        }

        "parse a string with timestamp with double precision fraction of a second" in {
          parseDate(".15") mustBe expectedLocalDateTime(15)
        }

        "parse a string with timestamp with tripple precision fraction of a second" in {
          parseDate(".159") mustBe expectedLocalDateTime(159)
        }

        "parse a string with timestamp with quadruple precision fraction of a second" in {
          parseDate(".1599") mustBe expectedLocalDateTime(1599)
        }

        "parse a string with timestamp with quintuple precision fraction of a second" in {
          parseDate(".15993") mustBe expectedLocalDateTime(15993)
        }
      }
    }
  }
}
