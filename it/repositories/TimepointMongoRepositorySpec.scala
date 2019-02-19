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

package repositories

import java.util.UUID

import helpers.SCRSMongoSpec
import play.api.libs.json.Json
import reactivemongo.api.commands.WriteResult

import scala.concurrent.ExecutionContext.Implicits.global

class TimepointMongoRepositorySpec extends SCRSMongoSpec {

  class Setup {
    val repository: TimepointMongoRepository = new TimepointMongo(reactiveMongoComponent).repo
    await(repository.drop)
    await(repository.ensureIndexes)

    def insertTimepoint(tp: String): WriteResult = {
      repository.collection.insert(TimePoint("CH-INCORPSTATUS-TIMEPOINT", tp))
    }

    def fetchTimepoint: Option[TimePoint] = await(repository.collection.find(Json.obj()).one[TimePoint])
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  def generateTimepoint: String = UUID.randomUUID().toString

  "TimepointRepository update" should {

    "be able to create a document when one doesn't exist" in new Setup {
      val timepoint = generateTimepoint

      val beforeCount: Int = await(repository.count)

      val oResult = await(repository.updateTimepoint(timepoint))

      await(repository.count) shouldBe (beforeCount + 1)

      val result = oResult

      result shouldBe timepoint
    }

    "be able to update the document when it does exist" in new Setup {
      val timepoint = generateTimepoint
      val oResult = await(repository.updateTimepoint(timepoint))

      val newTimepoint = "123456"
      val result = await(repository.updateTimepoint(newTimepoint))

      result shouldBe newTimepoint
    }
  }

  "Retrieving the Time point" should {

    "return nothing when there is no stored timepoint" in new Setup{
      val result = await(repository.retrieveTimePoint)

      result shouldBe None
    }

    "return an optional Time point when there is one stored" in new Setup{
      val timepoint = generateTimepoint

      await(repository.updateTimepoint(timepoint))

      val result = await(repository.retrieveTimePoint)

      result shouldBe Some(timepoint)
    }
  }

  "resetTimepointTo" should {

    val currentTimepoint = generateTimepoint
    val newTimepoint = generateTimepoint

    "reset an existing timepoint to the one provided" in new Setup {

      insertTimepoint(currentTimepoint)

      val result: Boolean = repository.resetTimepointTo(newTimepoint)
      result shouldBe true

      fetchTimepoint.get.timepoint shouldBe newTimepoint
    }


    "set the timepoint to the one provided when an existing timestamp doesn't exist" in new Setup {

      await(repository.count) shouldBe 0

      val result: Boolean = repository.resetTimepointTo(newTimepoint)
      result shouldBe true

      fetchTimepoint.get.timepoint shouldBe newTimepoint
    }
  }
}