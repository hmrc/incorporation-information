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

package repositories

import Helpers.{LogCapturing, SCRSSpec}
import ch.qos.logback.classic.Level
import models.IncorpUpdate
import play.api.Logger
import reactivemongo.api.commands.WriteError
import uk.gov.hmrc.mongo.MongoSpecSupport

class IncorpUpdateRepositorySpec extends SCRSSpec with MongoSpecSupport with LogCapturing {

  class Setup extends MongoErrorCodes {
    val repo = new IncorpUpdateMongoRepository(mongo, IncorpUpdate.mongoFormat)
  }

  "logUniqueIncorporations" should {

    def duplicateErrorMessage(transId: String) =
      s"""E11000 duplicate key error index: incorporation-information.incorporation-information.$$_id_ dup key: { : "$transId" }"""

    def incorpUpdates(transIds: String*): Seq[IncorpUpdate] = transIds.map{ transId =>
      IncorpUpdate(transId, "testStatus", None, None, "testTimepoint", None)
    }

    "log the transaction id's of the unique incorporations" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>

        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val errors = Seq(
          WriteError(0, ERR_DUPLICATE, duplicateErrorMessage("trans1")),
          WriteError(1, ERR_DUPLICATE, duplicateErrorMessage("trans3"))
        )

        val result = repo.nonDuplicateIncorporations(incorps, errors)

        result shouldBe incorpUpdates("trans2")

        logEvents.size shouldBe 1
        logEvents.head.getLevel shouldBe Level.INFO
        logEvents.head.getMessage shouldBe "[UniqueIncorp] transactionId : trans2"
      }
    }


    "log the transaction id's of the unique incorporations when a non-duplicate error ir present" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>

        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val errors = Seq(
          WriteError(0, ERR_DUPLICATE, duplicateErrorMessage("trans1")),
          WriteError(1, ERR_INVALID, "invalid"),
          WriteError(2, ERR_DUPLICATE, duplicateErrorMessage("trans3"))
        )

        val result = repo.nonDuplicateIncorporations(incorps, errors)

        result shouldBe incorpUpdates("trans2")

        logEvents.size shouldBe 1
        logEvents.head.getLevel shouldBe Level.INFO
        logEvents.head.getMessage shouldBe "[UniqueIncorp] transactionId : trans2"
      }
    }

    "not log anything when there are no unique incorporations" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>

        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val errors = Seq(
          WriteError(0, ERR_DUPLICATE, duplicateErrorMessage("trans1")),
          WriteError(0, ERR_DUPLICATE, duplicateErrorMessage("trans2")),
          WriteError(1, ERR_DUPLICATE, duplicateErrorMessage("trans3"))
        )

        val result = repo.nonDuplicateIncorporations(incorps, errors)
        result shouldBe incorpUpdates()

        logEvents.size shouldBe 0
      }
    }

    "log all incorporations when an empty error list is supplied" in new Setup {
      withCaptureOfLoggingFrom(Logger) { logEvents =>
        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val result = repo.nonDuplicateIncorporations(incorps, Seq.empty)
        result shouldBe incorps

        logEvents.size shouldBe 3
        logEvents.head.getLevel shouldBe Level.INFO
        logEvents.head.getMessage shouldBe "[UniqueIncorp] transactionId : trans1"
        logEvents.apply(1).getLevel shouldBe Level.INFO
        logEvents.apply(1).getMessage shouldBe "[UniqueIncorp] transactionId : trans2"
        logEvents.apply(2).getLevel shouldBe Level.INFO
        logEvents.apply(2).getMessage shouldBe "[UniqueIncorp] transactionId : trans3"
      }
    }
  }
}
