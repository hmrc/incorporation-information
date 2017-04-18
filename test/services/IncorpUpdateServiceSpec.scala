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

package services

import Helpers.JSONhelpers
import connectors.IncorporationCheckAPIConnector
import models.{IncorpUpdate, QueuedIncorpUpdate}
import org.joda.time.DateTime
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.commands.WriteError
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IncorpUpdateServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with JSONhelpers{

  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockIncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockTimepointRepository = mock[TimepointRepository]
  val mockQueueRepository = mock[QueueRepository]

  implicit val hc = HeaderCarrier()

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockIncorporationCheckAPIConnector)
    reset(mockIncorpUpdateRepository)
    reset(mockTimepointRepository)
    reset(mockQueueRepository)
  }

  trait mockService extends IncorpUpdateService {
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val incorpUpdateRepository = mockIncorpUpdateRepository
    val timepointRepository = mockTimepointRepository
    val queueRepository = mockQueueRepository
  }

  trait Setup {
    val service = new mockService {}
  }

  val timepoint = TimePoint("id", "old timepoint")
  val timepointOld = "old-timepoint"
  val timepointNew = "new-timepoint"
  val incorpUpdate = IncorpUpdate("transId", "accepted", None, None, timepointOld, None)
  val incorpUpdateNew = IncorpUpdate("transId", "accepted", None, None, timepointNew, None)
  val incorpUpdates = Seq(incorpUpdate, incorpUpdateNew)
  val emptyUpdates = Seq()
  val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now, incorpUpdate)

  "fetchIncorpUpdates" should {
    "return some updates" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(incorpUpdates))

      val response = service.fetchIncorpUpdates
      response.size shouldBe 2
    }

    "return no updates when they are no updates available" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))

      val response = service.fetchIncorpUpdates
      response.size shouldBe 0
    }
  }

  "storeIncorpUpdates" should {
    "return InsertResult(1, 0, Seq()) when one update has been inserted" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(InsertResult(1, 0, Seq()))

      val response = service.storeIncorpUpdates(Future.successful(incorpUpdates))
      response.map(ir => ir shouldBe InsertResult(1, 0, Seq()))
    }

    "return InsertResult(0, 0, Seq()) when there are no updates to store" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(InsertResult(0, 0, Seq()))

      val response = service.storeIncorpUpdates(Future.successful(emptyUpdates))
      response.map(ir => ir shouldBe InsertResult(0, 0, Seq()))
    }

    "return an InsertResult containing errors, when a failure occurred whilst adding incorp updates to the main collection" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      val writeError = WriteError(0, 121, "Invalid Incorp Update could not be stored")
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(InsertResult(0, 0, Seq(writeError)))

      val response = service.storeIncorpUpdates(emptyUpdates)
      response.map(ir => ir shouldBe InsertResult(0, 0, Seq(writeError)))
    }
  }

  "latestTimepoint" should {
    "return the latest timepoint when two have been given" in new Setup {
      val response = service.latestTimepoint(incorpUpdates)
      response shouldBe timepointNew
    }
  }

  "updateNextIncorpUpdateJobLot" should {

    "return a string stating that states 'No Incorporation updates were fetched'" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future(InsertResult(0, 0, Seq())))

      val response = await(service.updateNextIncorpUpdateJobLot)
      response shouldBe InsertResult(0,0,Seq())

    }

    "return a string stating that the timepoint has been updated to 'new timepoint'" in new Setup {
      val newTimepoint = timepointNew

      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepointOld)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Matchers.eq(Some(timepointOld)))(Matchers.any())).thenReturn(Future.successful(incorpUpdates))

      when(mockIncorpUpdateRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      val captor = ArgumentCaptor.forClass(classOf[String])

      when(mockTimepointRepository.updateTimepoint(Matchers.any())).thenReturn(Future.successful(newTimepoint))

      val response = await(service.updateNextIncorpUpdateJobLot)
      verify(mockTimepointRepository).updateTimepoint(captor.capture())
      captor.getValue shouldBe newTimepoint
      response shouldBe InsertResult(2,0,Seq())
    }
  }


  "createQueuedIncorpUpdate" should {
    "return a correctly formatted QueuedIncorpUpdate when given an IncorpUpdate" in new Setup {

      val fResult = service.createQueuedIncorpUpdate(Seq(incorpUpdate))
      val result = await(fResult)

      result.head.copy(timestamp = queuedIncorpUpdate.timestamp) shouldBe queuedIncorpUpdate
      result.head.timestamp.getMillis shouldBe (queuedIncorpUpdate.timestamp.getMillis +- 1000)
    }
  }

  "copyToQueue" should {
    "return true if a Seq of QueuedIncorpUpdates have been copied to the queue" in new Setup {
      when(mockQueueRepository.storeIncorpUpdates(Seq(queuedIncorpUpdate))).thenReturn(Future(InsertResult(1, 0, Seq())))

      val result = await(service.copyToQueue(Seq(queuedIncorpUpdate)))
      result shouldBe true
    }

    "return false if a Seq of QueuedIncorpUpdates have not been copied to the queue" in new Setup {
      when(mockQueueRepository.storeIncorpUpdates(Seq(queuedIncorpUpdate))).thenReturn(Future(InsertResult(0, 1, Seq())))

      val result = await(service.copyToQueue(Seq(queuedIncorpUpdate)))
      result shouldBe false
    }
  }
}


