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

import connectors.IncorporationCheckAPIConnector
import models.{IncorpUpdate, IncorpUpdatesResponse}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class IncorpUpdateServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockIncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockTimepointRepository = mock[TimepointRepository]

  implicit val hc = HeaderCarrier()

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockIncorporationCheckAPIConnector)
    reset(mockIncorpUpdateRepository)
    reset(mockTimepointRepository)
  }

  trait mockService extends IncorpUpdateService {
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val incorpUpdateRepository = mockIncorpUpdateRepository
    val timepointRepository = mockTimepointRepository
  }

  trait Setup {
    val service = new mockService {}
  }

  trait SetupMockedAudit {
    val service = new mockService {
      //override def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration, auditDetail: JsObject)(implicit hc: HeaderCarrier) = Future.successful(true)
    }
  }

  val timepoint = TimePoint("id", "old timepoint")
  val incorpUpdate = IncorpUpdate("transId", "accepted", None, None, timepoint.toString, None)
  val incorpUpdates = IncorpUpdatesResponse(Seq(incorpUpdate, incorpUpdate), "nextLink")
  val emptyUpdates = IncorpUpdatesResponse(Seq(), "nextLink")

  "fetchIncorpUpdates" should {
    "return some updates" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Some(timepoint.toString))).thenReturn(Future.successful(incorpUpdates))

      val response = service.fetchIncorpUpdates
      response.size shouldBe 2
    }

    "return no updates when they are no updates available" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))

      val response = service.fetchIncorpUpdates
      response.size shouldBe 0
    }
  }

  "storeIncorpUpdates" should {
    "return InsertResult(1, 0, Seq()) when one update has been inserted" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates.items)).thenReturn(InsertResult(1, 0, Seq()))

      val response = service.storeIncorpUpdates(Future.successful(incorpUpdates.items))
      response.map(ir => ir shouldBe InsertResult(1, 0, Seq()))
    }

    "return InsertResult(0, 0, Seq()) when there are no updates to store" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates.items)).thenReturn(InsertResult(0, 0, Seq()))

      val response = service.storeIncorpUpdates(Future.successful(emptyUpdates.items))
      response.map(ir => ir shouldBe InsertResult(0, 0, Seq()))
    }
  }

  "updateNextIncorpUpdateJobLot" should {

    "return a string stating that states 'No Incorporation updates were fetched'" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkSubmission(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates.items)).thenReturn(Future(InsertResult(0, 0, Seq())))

      val response = await(service.updateNextIncorpUpdateJobLot).toString
      response should include("No Incorporation updates were fetched")

    }

//    "return a string stating that the timepoint has been updated to 'new timepoint'" in new Setup {
//      val newTimepoint = "new timepoint"
//
//      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
//      when(mockIncorporationCheckAPIConnector.checkSubmission(Some(timepoint.toString))).thenReturn(Future.successful(incorpUpdates))
//      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates.items)).thenReturn(Future.successful(InsertResult(1, 0, Seq())))
//      when(mockTimepointRepository.updateTimepoint(newTimepoint)).thenReturn(Future.successful(newTimepoint))
//
//      val response = await(service.updateNextIncorpUpdateJobLot)
//      response shouldBe "" //include("Timepoint updated to new timepoint")
//    }


  }
}


