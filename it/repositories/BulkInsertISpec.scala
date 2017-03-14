/*
 * Copyright 2016 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.libs.concurrent.Promise
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class BulkInsertISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  class Setup {
    val repository = new BulkInsertMongoRepo
    await(repository.drop)
//    await(repository.ensureIndexes)
    def count = await(repository.count)
  }

  def docs(num: Int = 1) = (1 to num).map(n => IncorpUpdate(s"foo${n}", s"bar${n}"))

  "One by one" should {
    "insert a single doc" in new Setup {
      count shouldBe 0

      val fResponse = repository.storeUpdatesOneByOne(docs(1))

      await(fResponse) shouldBe 1
      count shouldBe 1
    }

    "insert a few docs" in new Setup {
      count shouldBe 0

      val num = 10

      val fResponse = repository.storeUpdatesOneByOne(docs(num))

      await(fResponse) shouldBe num
      count shouldBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count shouldBe 0

      await(repository.storeUpdatesOneByOne(docs(5)))

      val num = 10

      val fResponse = repository.storeUpdatesOneByOne(docs(num))

      await(fResponse) shouldBe num
      count shouldBe num
    }
  }

  "Bulk insert" should {
    "insert a single doc" in new Setup {
      count shouldBe 0

      val fResponse = repository.storeUpdatesAll(docs(1))

      val response = await(fResponse)
      response.n shouldBe 1
      count shouldBe 1
    }

    "insert a few docs" in new Setup {
      count shouldBe 0

      val num = 10

      val fResponse = repository.storeUpdatesAll(docs(num))

      val response = await(fResponse)
      response.n shouldBe num

      count shouldBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count shouldBe 0

      val numPart = 4

      await(repository.storeUpdatesAll(docs(numPart)))

      val num = 10

      val fResponse = repository.storeUpdatesAll(docs(num))

      val response = await(fResponse)
      response.n shouldBe num-numPart
      val errors = response.writeErrors
      errors.size shouldBe numPart
      errors.head.index shouldBe 0
      errors.head.code shouldBe 11000
      errors.head.errmsg should include("""dup key: { : "foo1" }""")

      count shouldBe num
    }
  }

}
