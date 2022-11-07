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

package repositories

import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import Helpers.SCRSSpec
import ch.qos.logback.classic.Level
import com.mongodb.bulk.BulkWriteError
import models.IncorpUpdate
import org.mongodb.scala.bson.BsonDocument
import play.api.Logger
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class IncorpUpdateRepositorySpec extends SCRSSpec with MongoSupport with LogCapturing {

  class Setup extends MongoErrorCodes {
    object Repository extends IncorpUpdateMongoRepository(mongoComponent, IncorpUpdate.mongoFormat)
  }

  "logUniqueIncorporations" must {

    def duplicateErrorMessage(transId: String) =
      s"""E11000 duplicate key error index: incorporation-information.incorporation-information.$$_id_ dup key: { : "$transId" }"""

    def incorpUpdates(transIds: String*): Seq[IncorpUpdate] = transIds.map{ transId =>
      IncorpUpdate(transId, "testStatus", None, None, "testTimepoint", None)
    }

    "log the transaction id's of the unique incorporations" in new Setup {
      withCaptureOfLoggingFrom(Repository.logger) { logEvents =>

        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val errors = Seq(
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans1"), BsonDocument(), 0),
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans3"), BsonDocument(), 1)
        )

        val result = Repository.nonDuplicateIncorporations(incorps, errors)

        result mustBe incorpUpdates("trans2")

        logEvents.size mustBe 1
        logEvents.head.getLevel mustBe Level.INFO
        logEvents.head.getMessage mustBe "[Repository][UniqueIncorp] transactionId : trans2"
      }
    }


    "log the transaction id's of the unique incorporations when a non-duplicate error ir present" in new Setup {
      withCaptureOfLoggingFrom(Repository.logger) { logEvents =>

        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val errors = Seq(
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans1"), BsonDocument(), 0),
          new BulkWriteError(ERR_INVALID, "invalid", BsonDocument(), 1),
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans3"), BsonDocument(), 2)
        )

        val result = Repository.nonDuplicateIncorporations(incorps, errors)

        result mustBe incorpUpdates("trans2")

        logEvents.size mustBe 1
        logEvents.head.getLevel mustBe Level.INFO
        logEvents.head.getMessage mustBe "[Repository][UniqueIncorp] transactionId : trans2"
      }
    }

    "not log anything when there are no unique incorporations" in new Setup {
      withCaptureOfLoggingFrom(Repository.logger) { logEvents =>

        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val errors = Seq(
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans1"), BsonDocument(), 0),
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans2"), BsonDocument(), 1),
          new BulkWriteError(ERR_DUPLICATE, duplicateErrorMessage("trans3"), BsonDocument(), 2)
        )

        val result = Repository.nonDuplicateIncorporations(incorps, errors)
        result mustBe incorpUpdates()

        logEvents.size mustBe 0
      }
    }

    "log all incorporations when an empty error list is supplied" in new Setup {
      withCaptureOfLoggingFrom(Repository.logger) { logEvents =>
        val incorps = incorpUpdates("trans1", "trans2", "trans3")

        val result = Repository.nonDuplicateIncorporations(incorps, Seq.empty)
        result mustBe incorps

        logEvents.size mustBe 3
        logEvents.head.getLevel mustBe Level.INFO
        logEvents.head.getMessage mustBe "[Repository][UniqueIncorp] transactionId : trans1"
        logEvents.apply(1).getLevel mustBe Level.INFO
        logEvents.apply(1).getMessage mustBe "[Repository][UniqueIncorp] transactionId : trans2"
        logEvents.apply(2).getLevel mustBe Level.INFO
        logEvents.apply(2).getMessage mustBe "[Repository][UniqueIncorp] transactionId : trans3"
      }
    }
  }
}
