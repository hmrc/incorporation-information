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

package test.repositories


import models.IncorpUpdate
import play.api.test.Helpers._
import repositories.{IncorpUpdateMongoImpl, MongoErrorCodes}
import test.helpers.SCRSMongoSpec

class IncorpUpdateRepositoryISpec extends SCRSMongoSpec with DocValidator {

  lazy val incorpRepo = app.injector.instanceOf[IncorpUpdateMongoImpl].repo

  class Setup extends MongoErrorCodes {

    await(incorpRepo.collection.drop().toFuture())
    await(incorpRepo.ensureIndexes)

    def count = await(incorpRepo.collection.countDocuments().toFuture())
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

  "storeIncorpUpdates" must {

    "insert a single document" in new Setup {
      count mustBe 0

      val fResponse = incorpRepo.storeIncorpUpdates(docs(1))

      val response = await(fResponse)
      response.inserted mustBe 1
      response.duplicate mustBe 0
      response.errors.size mustBe 0
      count mustBe 1
    }

    "insert 6 docs" in new Setup {
      count mustBe 0
      val num = 6

      val fResponse = incorpRepo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted mustBe num
      response.duplicate mustBe 0
      response.errors.size mustBe 0
      count mustBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count mustBe 0
      val numPart = 4

      await(incorpRepo.storeIncorpUpdates(docs(numPart)))
      count mustBe 4

      val num = 10
      val fResponse = incorpRepo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted mustBe num - numPart
      response.duplicate mustBe numPart
      response.errors.size mustBe 0
      count mustBe num
    }

    "insert some with failures" in new Setup {

      count mustBe 0
      val numPart = 4

      await(incorpRepo.storeIncorpUpdates(docs(numPart)))

      await(validateCRN("^bar[12345679]$"))

      val num = 9
      val fResponse = incorpRepo.storeIncorpUpdates(docs(num))

      val expectedNumErrors = 1
      val response = await(fResponse)

      response.errors.size mustBe expectedNumErrors
      response.inserted mustBe num - numPart - expectedNumErrors
      response.duplicate mustBe numPart
      response.errors.head.getCode mustBe ERR_INVALID
      response.errors.head.getMessage must include("""failed validation""")

      count mustBe num - expectedNumErrors
    }
  }

  "storeSingleIncorpUpdate" must {
    "insert a single document if it doesn't exist" in new Setup {
      count mustBe 0

      val response1 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(1)))

      response1.wasAcknowledged() mustBe true
      response1.getModifiedCount mustBe 0
      count mustBe 1
    }
    "insert another single document if it doesn't exist" in new Setup {

      await(incorpRepo.storeSingleIncorpUpdate(individualDoc(1)))
      val response2 = await(incorpRepo.storeSingleIncorpUpdate(individualDoc(2)))

      response2.wasAcknowledged() mustBe true
      response2.getModifiedCount mustBe 0
      count mustBe 2
    }

    "update a single document if it does exist" in new Setup {

      await(incorpRepo.storeSingleIncorpUpdate(individualDoc(1)))
      await(incorpRepo.storeSingleIncorpUpdate(individualDoc(2)))
      val response3 = await(incorpRepo.storeSingleIncorpUpdate(individualUpdatedDoc(1)))

      response3.wasAcknowledged() mustBe true
      response3.getModifiedCount mustBe 1
      count mustBe 2
    }
  }

  "getIncorpUpdate" must {

    val incorpUpdate = docs(1).head
    val transactionId = incorpUpdate.transactionId

    "find a document" in new Setup {
      count mustBe 0
      await(incorpRepo.storeIncorpUpdates(Seq(incorpUpdate)))

      val res = incorpRepo.getIncorpUpdate(transactionId)
      await(res) mustBe Some(incorpUpdate)
    }
  }
}
