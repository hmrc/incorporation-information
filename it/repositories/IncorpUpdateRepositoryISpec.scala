/*
 * Copyright 2020 HM Revenue & Customs
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
import models.IncorpUpdate
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands._
import reactivemongo.api.{BSONSerializationPack, FailoverStrategy, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONRegex}

import scala.concurrent.ExecutionContext.Implicits.global

trait DocValidator {
  val incorpRepo: IncorpUpdateMongoRepository

  val MONGO_RESULT_OK = Json.obj("ok" -> JsNumber(1))

  def validateCRN(regex: String = "^bar[1-7]$") = {

    val commandDoc = BSONDocument(
      "collMod" -> incorpRepo.collection.name,
      "validator" -> BSONDocument("company_number" -> BSONDocument("$regex" -> BSONRegex(regex, "")))
    )
    val runner = Command.run(BSONSerializationPack, FailoverStrategy.default)
    incorpRepo.collection.create() flatMap {
      _ => runner.apply(incorpRepo.collection.db, runner.rawCommand(commandDoc)).cursor(ReadPreference.primaryPreferred).head
    }
  }
}

class IncorpUpdateRepositoryISpec extends SCRSMongoSpec {

  class Setup extends MongoErrorCodes {
    val incorpRepo = new IncorpUpdateMongo {
      override val mongo: ReactiveMongoComponent = reactiveMongoComponent
    }.repo
    await(incorpRepo.drop)

    def count = await(incorpRepo.count)
  }

  def docs(num: Int = 1) = (1 to num).map(n => IncorpUpdate(
    transactionId = s"foo$n",
    status = "accepted",
    crn = Some(s"bar$n"),
    incorpDate = None,
    timepoint = s"tp$n",
    statusDescription = None))

  def individualDoc(num: Int = 1) = IncorpUpdate(
    transactionId = s"foo$num",
    status = "accepted",
    crn = Some(s"bar$num"),
    incorpDate = None,
    timepoint = s"tp$num",
    statusDescription = None)

  def individualUpdatedDoc(num: Int = 1) = IncorpUpdate(
    transactionId = s"foo$num",
    status = "accepted",
    crn = Some(s"updatedbar$num"),
    incorpDate = None,
    timepoint = s"tp$num",
    statusDescription = None)

  "storeIncorpUpdates" should {

    "insert a single document" in new Setup {
      count shouldBe 0

      val fResponse = incorpRepo.storeIncorpUpdates(docs(1))

      val response = await(fResponse)
      response.inserted shouldBe 1
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe 1
    }

    "insert 6 docs" in new Setup {
      count shouldBe 0
      val num = 6

      val fResponse = incorpRepo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count shouldBe 0
      val numPart = 4

      await(incorpRepo.storeIncorpUpdates(docs(numPart)))
      count shouldBe 4

      val num = 10
      val fResponse = incorpRepo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num - numPart
      response.duplicate shouldBe numPart
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some with failures" in new Setup with DocValidator {
      count shouldBe 0
      val numPart = 4
      val result = await(validateCRN("^bar[12345679]$"))

      await(incorpRepo.storeIncorpUpdates(docs(numPart)))

      val num = 9
      val fResponse = incorpRepo.storeIncorpUpdates(docs(num))

      val expectedNumErrors = 1
      val response = await(fResponse)

      response.errors.size shouldBe expectedNumErrors

      response.inserted shouldBe num - numPart - expectedNumErrors
      response.duplicate shouldBe numPart
      response.errors.head.index shouldBe 7
      response.errors.head.code shouldBe ERR_INVALID
      response.errors.head.errmsg should include("""failed validation""")

      count shouldBe num - expectedNumErrors
    }
  }

  "storeSingleIncorpUpdate" should {
    "insert a single document if it doesn't exist" in new Setup {
      count shouldBe 0

      val response1 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(1)))

      response1.ok shouldBe true
      response1.writeErrors shouldBe Seq()
      response1.nModified shouldBe 0
      count shouldBe 1
    }
    "insert another single document if it doesn't exist" in new Setup {

      val response1 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(1)))
      val response2 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(2)))

      response2.ok shouldBe true
      response2.writeErrors shouldBe Seq()
      response2.nModified shouldBe 0
      count shouldBe 2
    }

    "update a single document if it does exist" in new Setup {

      val response1 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(1)))
      val response2 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(2)))
      val response3 = await(incorpRepo.storeSingleIncorpUpdate(individualUpdatedDoc(1)))

      response3.ok shouldBe true
      response3.writeErrors shouldBe Seq()
      response3.nModified shouldBe 1
      count shouldBe 2
    }
  }

  "getIncorpUpdate" should {

    val incorpUpdate = docs(1).head
    val transactionId = incorpUpdate.transactionId

    "find a document" in new Setup {
      count shouldBe 0
      await(incorpRepo.storeIncorpUpdates(Seq(incorpUpdate)))

      val res = incorpRepo.getIncorpUpdate(transactionId)
      await(res) shouldBe Some(incorpUpdate)
    }
  }
}
