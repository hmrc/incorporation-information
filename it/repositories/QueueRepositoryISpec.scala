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
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.DateTime
import play.modules.reactivemongo.ReactiveMongoComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QueueRepositoryISpec extends SCRSMongoSpec {

  class Setup extends MongoErrorCodes {
    val repo = new QueueMongo {
      override val mongo: ReactiveMongoComponent = reactiveMongoComponent
    }.repo
    await(repo.drop)
    await(repo.ensureIndexes)

    def count = await(repo.count)

    def insert(u: QueuedIncorpUpdate) = await(fInsert(u))
    def fInsert(u: QueuedIncorpUpdate) = repo.insert(u)
  }

  val now = DateTime.now
  val transactionId = "12345"

  val update = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))
  val queuedUpdate = QueuedIncorpUpdate(now, update)

  def docs(num: Int = 1) = (1 to num).map(n =>
    QueuedIncorpUpdate(
      now,
      IncorpUpdate(
        transactionId = s"foo$n",
        status = "accepted",
        crn = Some(s"bar$n"),
        incorpDate = None,
        timepoint = s"tp$n",
        statusDescription = None
      )
    ))

  "removeQueuedIncorpUpdate" should {

    "remove the selected document and return true" in new Setup {
      insert(queuedUpdate)
      count shouldBe 1

      val result = await(repo.removeQueuedIncorpUpdate(transactionId))
      count shouldBe 0

      result shouldBe true
    }

    "return false if no document was deleted" in new Setup {
      count shouldBe 0

      val result = await(repo.removeQueuedIncorpUpdate(transactionId))
      result shouldBe false
    }
  }

  "getIncorpUpdates" should {

    "return the updates in the correct order #1" in new Setup {
      val baseTs = now
      val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
      val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
      val q2 = q1.copy(timestamp = baseTs.minusSeconds(50))

      await(insert(q1))
      await(insert(q2))

      val result = await(repo.getIncorpUpdates(10))

      result shouldBe Seq(q1, q2)
    }

    "return the updates in the correct order #2" in new Setup {
      val baseTs = now
      val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
      val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
      val q2 = q1.copy(timestamp = baseTs.minusSeconds(70))

      await(insert(q1))
      await(insert(q2))

      val result = await(repo.getIncorpUpdates(10))

      result shouldBe Seq(q2, q1)
    }

    "return the updates in the correct order, eliding the ones in the future" in new Setup {
      val baseTs = now
      val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
      val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
      val q2 = q1.copy(timestamp = baseTs.plusSeconds(60))
      val q3 = q1.copy(timestamp = baseTs.minusSeconds(70))
      val q4 = q1.copy(timestamp = baseTs.plusSeconds(70))
      val q5 = q1.copy(timestamp = baseTs.minusMillis(1))
      val q6 = q1.copy(timestamp = baseTs.minusSeconds(1))

      await(Future.sequence(Seq(q1,q2,q3,q4,q5,q6) map (fInsert(_))))

      val result = await(repo.getIncorpUpdates(10))

      result shouldBe Seq(q3, q1, q6, q5)
    }
  }

  "return the only 2 updates in the correct order, eliding the ones in the future" in new Setup {
    val baseTs = now
    val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
    val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
    val q2 = q1.copy(timestamp = baseTs.plusSeconds(60))
    val q3 = q1.copy(timestamp = baseTs.minusSeconds(70))
    val q4 = q1.copy(timestamp = baseTs.plusSeconds(70))
    val q5 = q1.copy(timestamp = baseTs.minusMillis(1))
    val q6 = q1.copy(timestamp = baseTs.minusSeconds(1))

    await(Future.sequence(Seq(q1,q2,q3,q4,q5,q6) map (fInsert(_))))

    val result = await(repo.getIncorpUpdates(2))

    result shouldBe Seq(q3, q1)
  }

  "getIncorpUpdate" should {

    "return an incorp update if one is found" in new Setup {
      insert(queuedUpdate)
      count shouldBe 1

      val result = await(repo.getIncorpUpdate(transactionId))
      result shouldBe Some(queuedUpdate)
    }

    "return a None if an update is not found" in new Setup {
      count shouldBe 0

      val result = await(repo.getIncorpUpdate(transactionId))
      result shouldBe None
    }
  }

  "BulkInserting documents" should {

    "insert a single document" in new Setup {
      count shouldBe 0

      val fResponse = repo.storeIncorpUpdates(docs(1))

      val response = await(fResponse)
      response.inserted shouldBe 1
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe 1
    }

    "insert duplicate documents" in new Setup {
      count shouldBe 0

      await(repo.storeIncorpUpdates(docs(1)))
      count shouldBe 1

      val response = await(repo.storeIncorpUpdates(docs(1)))
      response.inserted shouldBe 0
      response.duplicate shouldBe 1
      response.errors.size shouldBe 0
      count shouldBe 1
    }

    "insert 6 docs" in new Setup {
      count shouldBe 0
      val num = 6

      val fResponse = repo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num
      response.duplicate shouldBe 0
      response.errors.size shouldBe 0
      count shouldBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count shouldBe 0
      val numPart = 4

      await(repo.storeIncorpUpdates(docs(numPart)))
      count shouldBe 4

      val num = 10
      val fResponse = repo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted shouldBe num - numPart
      response.duplicate shouldBe numPart
      response.errors.size shouldBe 0
      count shouldBe num
    }
  }

  "updateTimestamp" should {
    "update a Timestamp of an existing queued incorp update" in new Setup {
      count shouldBe 0
      val numPart = 1
      await(repo.storeIncorpUpdates(docs(1)))
      count shouldBe 1

      val result = await(repo.getIncorpUpdate("foo1"))
      result.get.timestamp shouldBe now

      val newTS = DateTime.now.plusSeconds(10)
      await(repo.updateTimestamp("foo1", newTS))
      val updateResult = await(repo.getIncorpUpdate("foo1"))
      val updatedTimestamp = now.plusMinutes(10)

      updateResult.get.timestamp.getMillis shouldBe newTS.getMillis
    }
  }

}
