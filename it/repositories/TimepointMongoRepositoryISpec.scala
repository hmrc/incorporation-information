/*
 * Copyright 2023 HM Revenue & Customs
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

import helpers.SCRSMongoSpec
import play.api.test.Helpers._

import java.util.UUID

class TimepointMongoRepositoryISpec extends SCRSMongoSpec {

  lazy val repository: TimepointMongoRepository = app.injector.instanceOf[TimepointMongo].repo

  class Setup {
    await(repository.collection.drop().toFuture())
    await(repository.ensureIndexes)
  }

  def generateTimepoint: String = UUID.randomUUID().toString

  "TimepointRepository update" must {

    "be able to create a document when one doesn't exist" in new Setup {
      val timepoint = generateTimepoint

      val beforeCount = await(repository.collection.countDocuments().toFuture())

      val oResult = await(repository.updateTimepoint(timepoint))

      await(repository.collection.countDocuments().toFuture()) mustBe (beforeCount + 1)

      val result = oResult

      result mustBe timepoint
    }

    "be able to update the document when it does exist" in new Setup {

      val timepoint = generateTimepoint
      await(repository.updateTimepoint(timepoint))

      val newTimepoint = "123456"
      val result = await(repository.updateTimepoint(newTimepoint))

      result mustBe newTimepoint
    }
  }

  "Retrieving the Time point" must {

    "return nothing when there is no stored timepoint" in new Setup {
      val result = await(repository.retrieveTimePoint)

      result mustBe None
    }

    "return an optional Time point when there is one stored" in new Setup {
      val timepoint = generateTimepoint

      await(repository.updateTimepoint(timepoint))

      val result = await(repository.retrieveTimePoint)

      result mustBe Some(timepoint)
    }
  }
}