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
import play.api.libs.json.{JsNumber, Json}
import reactivemongo.api.BSONSerializationPack
import reactivemongo.api.commands._
import reactivemongo.bson.{BSONDocument, BSONRegex}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class BulkInsertISpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  class Setup extends MongoErrorCodes {
    val repository = new BulkInsertMongoRepo
    await(repository.drop)
//    await(repository.ensureIndexes)
    def count = await(repository.count)

  }

  trait DocValidator {
    val repository: BulkInsertMongoRepo

    val MONGO_RESULT_OK = Json.obj("ok" -> JsNumber(1))

    def validateCRN(regex: String = "^bar[1-7]$") = {
      // db.runCommand( {collMod: "incorp-info", validator: { crn: { $regex: /^bar[1-7]?$/  } } } )
      val commandDoc = BSONDocument(
        "collMod" -> repository.collection.name,
        "validator" -> BSONDocument("crn" -> BSONDocument("$regex" -> BSONRegex(regex, ""))
        )
      )
      val runner = Command.run(BSONSerializationPack)
      repository.collection.create() flatMap {
        _ => runner.apply(repository.collection.db, runner.rawCommand(commandDoc)).one[BSONDocument]
      }
    }
  }

  def docs(num: Int = 1) = (1 to num).map(n => IncorpUpdate(s"foo${n}", s"bar${n}"))

  "One by one" should {
    "insert a single doc" in new Setup {
      count shouldBe 0

      val fResponse = repository.storeUpdatesOneByOne(docs(1))

      val response = await(fResponse)
      response.inserted shouldBe 1
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe 1
    }

    "insert a few docs" in new Setup {
      count shouldBe 0
      val num = 6

      val fResponse = repository.storeUpdatesOneByOne(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count shouldBe 0
      val numPart = 4

      await(repository.storeUpdatesOneByOne(docs(numPart)))

      val num = 10
      val fResponse = repository.storeUpdatesOneByOne(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num-numPart
      response.duplicate shouldBe numPart
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some with failures" in new Setup with DocValidator {
      count shouldBe 0
      val numPart = 4
      val result = await(validateCRN("^bar[12345679]$"))

      await(repository.storeUpdatesAll(docs(numPart)))

      val num = 9
      val fResponse = repository.storeUpdatesOneByOne(docs(num))

      val expectedNumErrors = 1
      val response = await(fResponse)

      response.inserted shouldBe num-numPart-expectedNumErrors
      response.duplicate shouldBe numPart

      response.errors.size shouldBe 1
      response.errors.head.index shouldBe 0 // TODO incorrect index :-(
      response.errors.head.code shouldBe ERR_INVALID
      response.errors.head.errmsg should include("""failed validation""")

      count shouldBe num-expectedNumErrors
    }
  }

  "Bulk insert" should {
    "insert a single doc" in new Setup {
      count shouldBe 0

      val fResponse = repository.storeUpdatesAll(docs(1))

      val response = await(fResponse)
      response.inserted shouldBe 1
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe 1
    }

    "insert a few docs" in new Setup {
      count shouldBe 0
      val num = 6

      val fResponse = repository.storeUpdatesAll(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count shouldBe 0
      val numPart = 4

      await(repository.storeUpdatesAll(docs(numPart)))

      val num = 10
      val fResponse = repository.storeUpdatesAll(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num-numPart
      response.duplicate shouldBe numPart
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some with failures" in new Setup with DocValidator {
      count shouldBe 0
      val numPart = 4
      val result = await(validateCRN("^bar[12345679]$"))

      await(repository.storeUpdatesAll(docs(numPart)))

      val num = 9
      val fResponse = repository.storeUpdatesAll(docs(num))

      val expectedNumErrors = 1
      val response = await(fResponse)

      response.inserted shouldBe num-numPart-expectedNumErrors
      response.duplicate shouldBe numPart
      response.errors.size shouldBe 1
      response.errors.head.index shouldBe 7
      response.errors.head.code shouldBe ERR_INVALID
      response.errors.head.errmsg should include("""failed validation""")

      count shouldBe num-expectedNumErrors
    }
  }

}
