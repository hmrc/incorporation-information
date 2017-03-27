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

import scala.concurrent.ExecutionContext.Implicits.global

class TimepointMongoRepositorySpec extends SCRSMongoSpec { //UnitSpec with ScalaFutures with MongoSpecSupport with BeforeAndAfterEach with BeforeAndAfterAll with WithFakeApplication {

  class Setup {
    val repository = new TimepointMongo(reactiveMongoComponent).repo
    await(repository.drop)
    await(repository.ensureIndexes)
  }

  override def afterAll() = new Setup {
    await(repository.drop)
  }

  val timepoint = UUID.randomUUID().toString

  "TimepointRepository update" should {
    "be able to create a document when one doesn't exist" in new Setup {
      val randomTimepoint = timepoint

      val beforeCount: Int = await(repository.count)

      val oResult = await(repository.updateTimepoint(randomTimepoint))

      await(repository.count) shouldBe (beforeCount + 1)

      val result = oResult

      result shouldBe randomTimepoint
    }
    "be able to update the document when it does exist" in new Setup {
      val randomTimepoint = timepoint
      val oResult = await(repository.updateTimepoint(randomTimepoint))

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

      await(repository.updateTimepoint(timepoint))

      val result = await(repository.retrieveTimePoint)

      result shouldBe Some(timepoint)
    }
  }
}