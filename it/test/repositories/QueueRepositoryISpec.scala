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


import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.mongodb.scala.result.InsertOneResult
import play.api.test.Helpers._
import repositories.{MongoErrorCodes, QueueMongoImpl}
import test.helpers.SCRSMongoSpec
import utils.DateCalculators

import java.time.{Instant, ZoneOffset}
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class QueueRepositoryISpec extends SCRSMongoSpec with DateCalculators {

  lazy val repo = app.injector.instanceOf[QueueMongoImpl].repo

  class Setup extends MongoErrorCodes {

    await(repo.collection.drop.toFuture())
    await(repo.ensureIndexes)

    def count: Long = await(repo.collection.countDocuments().toFuture())

    def insert(u: QueuedIncorpUpdate): InsertOneResult = await(fInsert(u))

    def fInsert(u: QueuedIncorpUpdate): Future[InsertOneResult] = repo.collection.insertOne(u).toFuture()
  }

  val now = getDateTimeNowUTC.withNano(0)
  val transactionId = "12345"

  val update: IncorpUpdate = IncorpUpdate(transactionId, "rejected", None, None, "tp", Some("description"))
  val queuedUpdate: QueuedIncorpUpdate = QueuedIncorpUpdate(now, update)

  def docs(num: Int = 1): IndexedSeq[QueuedIncorpUpdate] = (1 to num).map(n =>
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

  "removeQueuedIncorpUpdate" must {

    "remove the selected document and return true" in new Setup {
      insert(queuedUpdate)
      count mustBe 1

      val result: Boolean = await(repo.removeQueuedIncorpUpdate(transactionId))
      count mustBe 0

      result mustBe true
    }

    "return false if no document was deleted" in new Setup {
      count mustBe 0

      val result: Boolean = await(repo.removeQueuedIncorpUpdate(transactionId))
      result mustBe false
    }
  }

  "getIncorpUpdates" must {

    "return the updates in the correct order #1" in new Setup {
      val baseTs = now
      val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
      val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
      val q2 = q1.copy(timestamp = baseTs.minusSeconds(50))

      insert(q1)
      insert(q2)

      val result = await(repo.getIncorpUpdates(10))

      result mustBe Seq(q1, q2)
    }

    "return the updates in the correct order #2" in new Setup {
      val baseTs = now
      val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
      val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
      val q2 = q1.copy(timestamp = baseTs.minusSeconds(70))

      insert(q1)
      insert(q2)

      val result = await(repo.getIncorpUpdates(10))

      result mustBe Seq(q2, q1)
    }

    "return the updates in the correct order, eliding the ones in the future" in new Setup {
      val baseTs = now
      val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
      val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
      val q2 = q1.copy(timestamp = baseTs.plusSeconds(60))
      val q3 = q1.copy(timestamp = baseTs.minusSeconds(70))
      val q4 = q1.copy(timestamp = baseTs.plusSeconds(70))
      val q5 = q1.copy(timestamp = baseTs.minus(1, ChronoUnit.MILLIS))
      val q6 = q1.copy(timestamp = baseTs.minusSeconds(1))

      await(Future.sequence(Seq(q1, q2, q3, q4, q5, q6) map fInsert))

      val result = await(repo.getIncorpUpdates(10))

      result mustBe Seq(q3, q1, q6, q5)
    }
  }

  "return the only 2 updates in the correct order, eliding the ones in the future" in new Setup {
    val baseTs = now
    val iu = IncorpUpdate("tx1", "status1", Some("crn1"), None, "xxxx")
    val q1 = QueuedIncorpUpdate(baseTs.minusSeconds(60), iu)
    val q2 = q1.copy(timestamp = baseTs.plusSeconds(60))
    val q3 = q1.copy(timestamp = baseTs.minusSeconds(70))
    val q4 = q1.copy(timestamp = baseTs.plusSeconds(70))
    val q5 = q1.copy(timestamp = baseTs.minus(1, ChronoUnit.MILLIS))
    val q6 = q1.copy(timestamp = baseTs.minusSeconds(1))

    await(Future.sequence(Seq(q1, q2, q3, q4, q5, q6) map (fInsert(_))))

    val result = await(repo.getIncorpUpdates(2))

    result mustBe Seq(q3, q1)
  }

  "getIncorpUpdate" must {

    "return an incorp update if one is found" in new Setup {
      insert(queuedUpdate)
      count mustBe 1

      val result = await(repo.getIncorpUpdate(transactionId))
      result mustBe Some(queuedUpdate)
    }

    "return a None if an update is not found" in new Setup {
      count mustBe 0

      val result = await(repo.getIncorpUpdate(transactionId))
      result mustBe None
    }
  }

  "BulkInserting documents" must {

    "insert a single document" in new Setup {
      count mustBe 0

      val fResponse = repo.storeIncorpUpdates(docs(1))

      val response = await(fResponse)
      response.inserted mustBe 1
      response.duplicate mustBe 0
      response.errors.size mustBe 0
      count mustBe 1
    }

    "insert duplicate documents" in new Setup {
      count mustBe 0

      await(repo.storeIncorpUpdates(docs(1)))
      count mustBe 1

      val response = await(repo.storeIncorpUpdates(docs(1)))
      response.inserted mustBe 0
      response.duplicate mustBe 1
      response.errors.size mustBe 0
      count mustBe 1
    }

    "insert 6 docs" in new Setup {
      count mustBe 0
      val num = 6

      val fResponse = repo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)
      response.inserted mustBe num
      response.duplicate mustBe 0
      response.errors.size mustBe 0
      count mustBe num
    }

    "insert some, then insert them again with more" in new Setup {
      count mustBe 0
      val numPart = 4

      await(repo.storeIncorpUpdates(docs(numPart)))
      count mustBe 4

      val num = 10
      val fResponse = repo.storeIncorpUpdates(docs(num))

      val response = await(fResponse)

      response.inserted mustBe num - numPart
      response.duplicate mustBe numPart
      response.errors.size mustBe 0
      count mustBe num
    }
  }

  "updateTimestamp" must {
    "update a Timestamp of an existing queued incorp update" in new Setup {
      count mustBe 0

      await(repo.storeIncorpUpdates(docs(1)))
      count mustBe 1

      val result = await(repo.getIncorpUpdate("foo1"))
      result.get.timestamp mustBe now

      val newTS = Instant.now.plusSeconds(10)
      await(repo.updateTimestamp("foo1", newTS))
      val updateResult = await(repo.getIncorpUpdate("foo1"))

      updateResult.get.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli mustBe newTS.toEpochMilli
    }
  }

}
