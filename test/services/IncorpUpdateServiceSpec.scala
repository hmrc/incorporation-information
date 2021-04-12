/*
 * Copyright 2021 HM Revenue & Customs
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

import java.time.LocalTime

import Helpers.{JSONhelpers, LogCapturing, SCRSSpec}
import connectors.IncorporationAPIConnector
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, Matchers}
import play.api.Logger
import play.api.test.Helpers._
import reactivemongo.api.commands.{UpdateWriteResult, Upserted, WriteError}
import reactivemongo.bson.BSONString
import repositories._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockKeeper
import utils.{DateCalculators, PagerDutyKeys}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IncorpUpdateServiceSpec extends SCRSSpec with JSONhelpers with LogCapturing {

  val mockIncorporationCheckAPIConnector = mock[IncorporationAPIConnector]
  val mockIncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockTimepointRepository = mock[TimepointRepository]
  val mockQueueRepository = mock[QueueRepository]
  val mockSubscriptionService = mock[SubscriptionService]
  val mockLockKeeper: LockKeeper = mock[LockKeeper]
  val mockSubRepo = mock[SubscriptionsMongoRepository]

  implicit val hc = HeaderCarrier()

  def resetMocks() {
    reset(
      mockIncorporationCheckAPIConnector,
      mockIncorpUpdateRepository,
      mockSubscriptionService,
      mockTimepointRepository,
      mockQueueRepository,
      mockSubRepo,
      mockLockKeeper
    )
  }

  class Setup(dc: DateCalculators = new DateCalculators {}, days: String = "MON,FRI") {
    def service = new IncorpUpdateService {
      override val dateCalculators: DateCalculators = dc
      val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
      val incorpUpdateRepository = mockIncorpUpdateRepository
      val timepointRepository = mockTimepointRepository
      val queueRepository = mockQueueRepository
      val subscriptionService = mockSubscriptionService
      override val lockKeeper: LockKeeper = mockLockKeeper
      val loggingDays = days
      val loggingTimes = "08:00:00_17:00:00"
    }

    resetMocks()
  }

  val transId = "transId1"
  val transId2 = "transId2"

  val timepoint = TimePoint("id", "old timepoint")
  val timepointOld = "old-timepoint"
  val timepointNew = "new-timepoint"
  val timepointSeq = Seq(timepointOld, timepointNew)
  val incorpUpdate = IncorpUpdate(transId, "accepted", None, None, timepointOld, None)
  val incorpUpdate2 = IncorpUpdate(transId2, "accepted", None, None, timepointOld, None)
  val incorpUpdate3 = IncorpUpdate("transId3", "rejected", None, None, timepointOld, None)
  val incorpUpdateNew = IncorpUpdate("transIdNew", "accepted", None, None, timepointNew, None)
  val incorpUpdates = Seq(incorpUpdate, incorpUpdateNew)
  val seqOfIncorpUpdates = Seq(incorpUpdate, incorpUpdate2, incorpUpdate3)
  val emptyUpdates = Seq()
  val queuedIncorpUpdate = QueuedIncorpUpdate(DateTime.now, incorpUpdate)
  val queuedIncorpUpdate2 = QueuedIncorpUpdate(DateTime.now, incorpUpdateNew)
  val regimeCT = "ct"
  val regimeCTAX = "ctax"
  val subscriber = "scrs"
  val url = "www.test.com"
  val subCT = Subscription(transId, regimeCT, subscriber, url)
  val subCTAX = Subscription(transId2, regimeCTAX, subscriber, url)

  "fetchIncorpUpdates" should {
    "return some updates" in new Setup {
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(subCT)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(incorpUpdates))

      val response = await(service.fetchIncorpUpdates)
      response.size shouldBe 2
    }

    "return no updates when they are no updates available" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))

      val response = await(service.fetchIncorpUpdates)
      response.size shouldBe 0
    }
  }

  "fetchSpecificIncorpUpdates" should {
    "return a single update" in new Setup {
      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Some(timepointOld))).thenReturn(Future.successful(Seq(incorpUpdate)))

      val response = await(service.fetchSpecificIncorpUpdates(Some(timepointOld)))
      response shouldBe incorpUpdate
    }
  }


  "storeIncorpUpdates" should {

    "return InsertResult(2, 0, Seq(), 0) when one update with 2 incorps has been inserted with CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(subCT)))

      val response = await(service.storeIncorpUpdates(incorpUpdates))
      response shouldBe InsertResult(2, 0, Seq(), 0, incorpUpdates)
    }

    "return InsertResult(2, 0, Seq(), 2) when one update with 2 incorps has been inserted without CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(None))

      val response = await(service.storeIncorpUpdates(incorpUpdates))
      response shouldBe InsertResult(2, 0, Seq(), 2, incorpUpdates)
    }

    "return InsertResult(2, 0, Seq(), 1) when one update with 2 incorps has been inserted one with one without CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))
      when(mockSubscriptionService.getSubscription(any(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(subCT)), Future.successful(None), Future.successful(None))

      val response = await(service.storeIncorpUpdates(incorpUpdates))
      response shouldBe InsertResult(2, 0, Seq(), 1, incorpUpdates)
    }

    "return InsertResult(0, 0, Seq()) when there are no updates to store" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future.successful(InsertResult(0, 0, Seq())))

      val response = await(service.storeIncorpUpdates(emptyUpdates))
      response shouldBe InsertResult(0, 0, Seq(), 0)
    }

    "return an InsertResult containing errors, when a failure occurred whilst adding incorp updates to the main collection" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      val writeError = WriteError(0, 121, "Invalid Incorp Update could not be stored")
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future.successful(InsertResult(0, 0, Seq(writeError))))

      val response = await(service.storeIncorpUpdates(emptyUpdates))
      response shouldBe InsertResult(0, 0, Seq(writeError))
    }
  }

  "storeSpecificIncorpUpdate" should {
    "return an UpdateWriteResult when a single IncorpUpdate is input" in new Setup {
      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)
      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(incorpUpdate)).thenReturn(Future.successful(UWR))

      val response = await(service.storeSpecificIncorpUpdate(incorpUpdate))
      response shouldBe UpdateWriteResult(true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)
    }
  }
  "timepointValidator" should {
    val todaysDate = new DateTime("2019-01-07T00:00:00.005")
    val cohoString = 20190107000000005L
    val todaysDateCoho = cohoString.toString
    val todaysDateToCohoMinus1 = 20190107000000004L
    val todaysDateToCohoPlus1 = 20190107010000006L

    def nDCalc(date: DateTime): DateCalculators = new DateCalculators {
      override def getDateNowUkZonedTime: DateTime = date
    }

    "return false if timepoint < now" in new Setup(nDCalc(todaysDate)) {
      service.timepointValidator(todaysDateToCohoMinus1.toString) shouldBe false
    }
    "return false if timepoint = now" in new Setup(nDCalc(todaysDate)) {
      service.timepointValidator(todaysDateCoho) shouldBe false
    }
    "return true if timepoint > now" in new Setup(nDCalc(todaysDate)) {

      service.timepointValidator(todaysDateToCohoPlus1.toString) shouldBe true
    }

    "return true if timepoint cannot be parsed" in new Setup {
      service.timepointValidator("foo") shouldBe true
    }
  }

  "latestTimepoint" should {
    val mondayWorking = new DateTime("2019-01-07T00:00:00.005")
    val goodTimePoint = 20190104000000005L
    val previousGoodTimePoint = goodTimePoint - 1

    val badDateTimePointWorkingDay = 20190111000000006L
    val badNonParsableTimePoint = "Foobar"


    val inu1 = IncorpUpdate("transIdNew", "accepted", None, None, goodTimePoint.toString, None)
    val inu2Bad = IncorpUpdate("transIdNew", "accepted", None, None, badDateTimePointWorkingDay.toString, None)
    val incorpUpdatesGoodAndBad = Seq(inu1, inu2Bad)

    val inu3 = IncorpUpdate("transIdNew", "accepted", None, None, previousGoodTimePoint.toString, None)
    val incorpUpdatesGoodOnlyRealTimepoints = Seq(inu1, inu3)

    val inu4BADNonParsable = IncorpUpdate("transIdNew", "accepted", None, None, badNonParsableTimePoint, None)
    val incorpUpdatesNonParsable = Seq(inu4BADNonParsable)


    def time(h: Int, m: Int, s: Int) = LocalTime.of(h, m, s)

    def nDCalc(time: LocalTime, date: DateTime): DateCalculators = new DateCalculators {
      override def getCurrentTime: LocalTime = time

      override def getDateNowUkZonedTime: DateTime = date
    }

    "throw a PAGER DUTY log message if working day true and timepoint > now" in new Setup(nDCalc(time(8, 0, 1), mondayWorking)) {
      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        service.latestTimepoint(incorpUpdatesGoodAndBad) shouldBe badDateTimePointWorkingDay.toString
        loggingEvents.head.getMessage shouldBe s"${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: $badDateTimePointWorkingDay"
      }
    }
    "throw a PAGER DUTY log message at level ERROR if working day true and timepoint cannot be parsed" in new Setup(nDCalc(time(8, 0, 1), mondayWorking)) {
      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        service.latestTimepoint(incorpUpdatesNonParsable) shouldBe badNonParsableTimePoint.toString
        loggingEvents.head.getMessage shouldBe "couldn't parse Foobar"
        loggingEvents.tail.head.getMessage shouldBe s"${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: ${inu4BADNonParsable.timepoint}"
      }
    }

    "throw a PAGER DUTY log message if working day false, timepoint > now" in new Setup(nDCalc(time(7, 0, 1), mondayWorking.plusDays(1))) {
      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        service.latestTimepoint(incorpUpdatesGoodAndBad) shouldBe badDateTimePointWorkingDay.toString
        loggingEvents.head.getMessage shouldBe s"${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: $badDateTimePointWorkingDay"
      }
    }
    "don't throw a log message if working day true, timepoint < now" in new Setup(nDCalc(time(8, 0, 1), mondayWorking)) {
      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        service.latestTimepoint(incorpUpdatesGoodOnlyRealTimepoints) shouldBe previousGoodTimePoint.toString
        loggingEvents.size shouldBe 0
      }
    }
    "don't throw a log message if working day true, timepoint = now" in new Setup(nDCalc(time(8, 0, 1), mondayWorking)) {
      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        service.latestTimepoint(incorpUpdatesGoodOnlyRealTimepoints) shouldBe previousGoodTimePoint.toString
        loggingEvents.size shouldBe 0
      }
    }
    "return the latest timepoint when two have been given" in new Setup {
      val response = service.latestTimepoint(incorpUpdatesGoodOnlyRealTimepoints)
      response shouldBe previousGoodTimePoint.toString
    }
  }

  "updateNextIncorpUpdateJobLot" should {

    "return a string stating that states 'No Incorporation updates were fetched'" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future(InsertResult(0, 0, Seq())))

      val response = await(service.updateNextIncorpUpdateJobLot)
      response shouldBe InsertResult(0, 0, Seq())

    }

    "return a string stating that the timepoint has been updated to 'new timepoint' (which is actually non parsable)" in new Setup {
      val newTimepoint = timepointNew
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(subCT)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepointOld)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Matchers.eq(Some(timepointOld)))(Matchers.any())).thenReturn(Future.successful(incorpUpdates))

      when(mockIncorpUpdateRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      val captor = ArgumentCaptor.forClass(classOf[String])

      when(mockTimepointRepository.updateTimepoint(Matchers.any())).thenReturn(Future.successful(newTimepoint))

      val response = await(service.updateNextIncorpUpdateJobLot)
      verify(mockTimepointRepository).updateTimepoint(captor.capture())
      captor.getValue shouldBe newTimepoint
      response shouldBe InsertResult(2, 0, Seq(), 0, incorpUpdates)
    }
    "return a string stating that the timepoint has been updated to '2016010101000'" in new Setup {
      val iup1 = IncorpUpdate("transIdNew", "accepted", None, None, "2016010101000", None)
      val iup2 = IncorpUpdate("transIdNew", "accepted", None, None, "201501010100", None)
      val incorpUpdatesvalidTP = Seq(iup2, iup1)
      when(mockSubscriptionService.getSubscription(Matchers.anyString(), Matchers.anyString(), Matchers.anyString()))
        .thenReturn(Future(Some(subCT)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepointOld)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Matchers.eq(Some(timepointOld)))(Matchers.any())).thenReturn(Future.successful(incorpUpdatesvalidTP))

      when(mockIncorpUpdateRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdatesvalidTP)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      val captor = ArgumentCaptor.forClass(classOf[String])

      when(mockTimepointRepository.updateTimepoint(Matchers.any())).thenReturn(Future.successful("2016010101000"))

      val response = await(service.updateNextIncorpUpdateJobLot)
      verify(mockTimepointRepository).updateTimepoint(captor.capture())
      captor.getValue shouldBe "2016010101000"
      response shouldBe InsertResult(2, 0, Seq(), 0, incorpUpdatesvalidTP)
    }
  }

  "updateSpecificIncorpUpdateByTP" should {

    "return a Sequence of trues when a sequence of TPs is input and there is a queue entry for each" in new Setup {

      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(ok = true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)

      val seqOfQueuedIncorpUpdates = Seq(queuedIncorpUpdate, queuedIncorpUpdate2)

      when(mockQueueRepository.removeQueuedIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(Some(queuedIncorpUpdate)))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(Matchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any()))
        .thenReturn(Future.successful(InsertResult(1, 0, Nil)))

      val response = await(service.updateSpecificIncorpUpdateByTP(timepointSeq))
      response shouldBe Seq(true, true)

    }
    "return a Sequence of false when a sequence of TPs is input and queue entries don't exist" in new Setup {

      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(ok = true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)

      val seqOfQueuedIncorpUpdates = Seq(queuedIncorpUpdate, queuedIncorpUpdate2)

      when(mockQueueRepository.removeQueuedIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(Matchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any()))
        .thenReturn(Future.successful(InsertResult(1, 0, Nil)))

      val response = await(service.updateSpecificIncorpUpdateByTP(timepointSeq))
      response shouldBe Seq(false, false)

    }

    "return a Sequence of trues when a sequence of TPs is input and queue entries don't exist but the for no queue switch is set" in new Setup {

      val upserted = Seq(Upserted(1, BSONString("")))
      val UWR = UpdateWriteResult(ok = true, 1, 1, upserted, Seq(WriteError(1, 1, "")), None, None, None)

      val seqOfQueuedIncorpUpdates = Seq(queuedIncorpUpdate, queuedIncorpUpdate2)

      when(mockQueueRepository.removeQueuedIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(Matchers.any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(Matchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Matchers.any())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(Matchers.any()))
        .thenReturn(Future.successful(InsertResult(1, 0, Nil)))

      val response = await(service.updateSpecificIncorpUpdateByTP(timepointSeq, true))
      response shouldBe Seq(true, true)

    }

  }

  "createQueuedIncorpUpdate" should {
    "return a correctly formatted QueuedIncorpUpdate when given an IncorpUpdate" in new Setup {

      val result = service.createQueuedIncorpUpdates(Seq(incorpUpdate))

      result.head.copy(timestamp = queuedIncorpUpdate.timestamp) shouldBe queuedIncorpUpdate
      result.head.timestamp.getMillis shouldBe (queuedIncorpUpdate.timestamp.getMillis +- 1500)
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

  "alertOnNoCTInterest" should {

    "return 0 and not raise an alert when there's an interest registered" in new Setup {
      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(Some(subCT)))

      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        val result = await(service.alertOnNoCTInterest(seqOfIncorpUpdates))
        result shouldBe 0

        loggingEvents.isEmpty shouldBe true
      }
    }

    "return 2 where there were two incorps without an interest registered for regime ct or ctax" in new Setup(days = "FOO") {
      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(None))

      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCTAX), eqTo(subscriber)))
        .thenReturn(Future(None))

      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        val result = await(service.alertOnNoCTInterest(incorpUpdates))
        result shouldBe 2

        loggingEvents.size shouldBe 2
      }
    }

    "return 1 alerts when 2 out of 3 incorps have an interested registered" in new Setup(days = "BAR") {
      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(Some(subCT)), Future(None), Future(Some(subCT)))

      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCTAX), eqTo(subscriber)))
        .thenReturn(Future(None))

      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        val result = await(service.alertOnNoCTInterest(seqOfIncorpUpdates))
        result shouldBe 1

        loggingEvents.size shouldBe 1
      }
    }

    "return 0 when there is 1 incorp update with a ctax regime" in new Setup {
      when(mockSubscriptionService.getSubscription(eqTo(transId), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(None))

      when(mockSubscriptionService.getSubscription(eqTo(transId), eqTo(regimeCTAX), eqTo(subscriber)))
        .thenReturn(Future(Some(subCT)))

      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        val result = await(service.alertOnNoCTInterest(Seq(incorpUpdate)))
        result shouldBe 0

        loggingEvents.size shouldBe 0
      }
    }

    "return 0 when there are 2 incorp updates; 1 with a ct regime sub and 1 with a ctax regime sub" in new Setup {
      when(mockSubscriptionService.getSubscription(any(), any(), eqTo(subscriber)))
        .thenReturn(Future.successful(Some(subCT)), Future.successful(None), Future.successful(Some(subCTAX)))

      withCaptureOfLoggingFrom(Logger) { loggingEvents =>
        val result = await(service.alertOnNoCTInterest(Seq(incorpUpdate, incorpUpdate2)))
        result shouldBe 0

        loggingEvents.size shouldBe 0

        verify(mockSubscriptionService, times(1)).getSubscription(eqTo(transId), eqTo(regimeCT), eqTo(subscriber))
        verify(mockSubscriptionService, times(1)).getSubscription(eqTo(transId2), eqTo(regimeCTAX), eqTo(subscriber))
      }
    }

    "return 0 alerts if no incorps are processed" in new Setup {
      val result = await(service.alertOnNoCTInterest(emptyUpdates))
      result shouldBe 0
    }
  }
}
