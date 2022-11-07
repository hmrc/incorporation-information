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

import ch.qos.logback.classic.Level
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class LoggingSpec extends PlaySpec with LogCapturing {

  val ex = new Exception("foobar")

  object TestLogging extends Logging

  "have the logger configured correctly" in {
    TestLogging.logger.logger.getName mustBe "application.utils.TestLogging"
  }

  "when logging" must {

    withCaptureOfLoggingFrom(TestLogging.logger) { logs =>
      Seq(
        Level.DEBUG -> TestLogging.logger.debug(s"${Level.DEBUG} Message"),
        Level.INFO -> TestLogging.logger.info(s"${Level.INFO} Message"),
        Level.WARN -> TestLogging.logger.warn(s"${Level.WARN} Message"),
        Level.ERROR -> TestLogging.logger.error(s"${Level.ERROR} Message")
      ) foreach {

        case (level, _) =>

          s"at level $level" must {

            s"output the correct message and level (prefixing with the class/object name)" in {
              logs.exists(log => log.getMessage == s"[TestLogging] $level Message" && log.getLevel == level) mustBe true
            }
          }
      }
    }
  }
}
