/*
 * Copyright 2023 HM Revenue & Customs
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

import Helpers.{JSONhelpers, SCRSSpec}
import com.mongodb.client.result.UpdateResult
import connectors.IncorporationAPIConnector
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mongodb.scala.WriteError
import org.mongodb.scala.bson.{BsonDocument, BsonString}
import play.api.test.Helpers._
import repositories._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import utils.{DateCalculators, PagerDutyKeys}

import java.time.{LocalDateTime, LocalTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class IncorpUpdateServiceSpec extends SCRSSpec with JSONhelpers with LogCapturing with DateCalculators {

  val mockIncorporationCheckAPIConnector = mock[IncorporationAPIConnector]
  val mockIncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockTimepointRepository = mock[TimepointRepository]
  val mockQueueRepository = mock[QueueRepository]
  val mockSubscriptionService = mock[SubscriptionService]
  val mockLockService: LockService = mock[LockService]
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
      mockLockService
    )
  }

  class Setup(dc: DateCalculators = new DateCalculators {}, days: String = "MON,FRI") {
    object Service extends IncorpUpdateService {
      override val dateCalculators: DateCalculators = dc
      val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
      val incorpUpdateRepository = mockIncorpUpdateRepository
      val timepointRepository = mockTimepointRepository
      val queueRepository = mockQueueRepository
      val subscriptionService = mockSubscriptionService
      override val lockKeeper: LockService = mockLockService
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
  val queuedIncorpUpdate = QueuedIncorpUpdate(getDateTimeNowUTC, incorpUpdate)
  val queuedIncorpUpdate2 = QueuedIncorpUpdate(getDateTimeNowUTC, incorpUpdateNew)
  val regimeCT = "ct"
  val regimeCTAX = "ctax"
  val subscriber = "scrs"
  val url = "www.test.com"
  val subCT = Subscription(transId, regimeCT, subscriber, url)
  val subCTAX = Subscription(transId2, regimeCTAX, subscriber, url)

  "fetchIncorpUpdates" must {
    "return some updates" in new Setup {
      when(mockSubscriptionService.getSubscription(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future(Some(subCT)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(incorpUpdates))

      val response = await(Service.fetchIncorpUpdates)
      response.size mustBe 2
    }

    "return no updates when they are no updates available" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))

      val response = await(Service.fetchIncorpUpdates)
      response.size mustBe 0
    }
  }

  "fetchSpecificIncorpUpdates" must {
    "return a single update" in new Setup {
      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(Some(timepointOld))).thenReturn(Future.successful(Seq(incorpUpdate)))

      val response = await(Service.fetchSpecificIncorpUpdates(Some(timepointOld)))
      response mustBe incorpUpdate
    }
  }


  "storeIncorpUpdates" must {

    "return InsertResult(2, 0, Seq(), 0) when one update with 2 incorps has been inserted with CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))
      when(mockSubscriptionService.getSubscription(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future(Some(subCT)))

      val response = await(Service.storeIncorpUpdates(incorpUpdates))
      response mustBe InsertResult(2, 0, Seq(), 0, incorpUpdates)
    }

    "return InsertResult(2, 0, Seq(), 2) when one update with 2 incorps has been inserted without CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))
      when(mockSubscriptionService.getSubscription(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future(None))

      val response = await(Service.storeIncorpUpdates(incorpUpdates))
      response mustBe InsertResult(2, 0, Seq(), 2, incorpUpdates)
    }

    "return InsertResult(2, 0, Seq(), 1) when one update with 2 incorps has been inserted one with one without CT subscriptions" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(incorpUpdates)).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))
      when(mockSubscriptionService.getSubscription(any(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future(Some(subCT)), Future.successful(None), Future.successful(None))

      val response = await(Service.storeIncorpUpdates(incorpUpdates))
      response mustBe InsertResult(2, 0, Seq(), 1, incorpUpdates)
    }

    "return InsertResult(0, 0, Seq()) when there are no updates to store" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future.successful(InsertResult(0, 0, Seq())))

      val response = await(Service.storeIncorpUpdates(emptyUpdates))
      response mustBe InsertResult(0, 0, Seq(), 0)
    }

    "return an InsertResult containing errors, when a failure occurred whilst adding incorp updates to the main collection" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      val writeError = new WriteError(121, "Invalid Incorp Update could not be stored", BsonDocument())
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future.successful(InsertResult(0, 0, Seq(writeError))))

      val response = await(Service.storeIncorpUpdates(emptyUpdates))
      response mustBe InsertResult(0, 0, Seq(writeError))
    }
  }

  "storeSpecificIncorpUpdate" must {
    "return an UpdateWriteResult when a single IncorpUpdate is input" in new Setup {

      val UWR = UpdateResult.acknowledged(1, 1, BsonString("newUpsertedId"))
      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(incorpUpdate)).thenReturn(Future.successful(UWR))

      val response = await(Service.storeSpecificIncorpUpdate(incorpUpdate))
      response mustBe UWR
    }
  }
  "timepointValidator" must {
    val todaysDate = LocalDateTime.parse("2019-01-07T00:00:00.005")
    val cohoString = 20190107000000005L
    val todaysDateCoho = cohoString.toString
    val todaysDateToCohoMinus1 = 20190107000000004L
    val todaysDateToCohoPlus1 = 20190107010000006L

    def nDCalc(date: LocalDateTime): DateCalculators = new DateCalculators {
      override def getDateTimeNowGMT: LocalDateTime = date
    }

    "return false if timepoint < now" in new Setup(nDCalc(todaysDate)) {
      Service.timepointValidator(todaysDateToCohoMinus1.toString) mustBe false
    }
    "return false if timepoint = now" in new Setup(nDCalc(todaysDate)) {
      Service.timepointValidator(todaysDateCoho) mustBe false
    }
    "return true if timepoint > now" in new Setup(nDCalc(todaysDate)) {

      Service.timepointValidator(todaysDateToCohoPlus1.toString) mustBe true
    }

    "return true if timepoint cannot be parsed" in new Setup {
      Service.timepointValidator("foo") mustBe true
    }
  }

  "latestTimepoint" must {
    val mondayWorking = LocalDateTime.parse("2019-01-07T00:00:00.005")
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

    def nDCalc(date: LocalDateTime): DateCalculators = new DateCalculators {

      override def getDateTimeNowGMT: LocalDateTime = date
    }

    "throw a PAGER DUTY log message if working day true and timepoint > now" in new Setup(nDCalc(mondayWorking)) {
      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        Service.latestTimepoint(incorpUpdatesGoodAndBad) mustBe badDateTimePointWorkingDay.toString
        loggingEvents.head.getMessage mustBe s"[Service] ${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: $badDateTimePointWorkingDay"
      }
    }
    "throw a PAGER DUTY log message at level ERROR if working day true and timepoint cannot be parsed" in new Setup(nDCalc(mondayWorking)) {
      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        Service.latestTimepoint(incorpUpdatesNonParsable) mustBe badNonParsableTimePoint.toString
        loggingEvents.head.getMessage mustBe "[Service][timepointValidator] couldn't parse Foobar"
        loggingEvents.tail.head.getMessage mustBe s"[Service] ${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: ${inu4BADNonParsable.timepoint}"
      }
    }

    "throw a PAGER DUTY log message if working day false, timepoint > now" in new Setup(nDCalc(mondayWorking.plusDays(1))) {
      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        Service.latestTimepoint(incorpUpdatesGoodAndBad) mustBe badDateTimePointWorkingDay.toString
        loggingEvents.head.getMessage mustBe s"[Service] ${PagerDutyKeys.TIMEPOINT_INVALID} - last timepoint received from coho invalid: $badDateTimePointWorkingDay"
      }
    }
    "don't throw a log message if working day true, timepoint < now" in new Setup(nDCalc(mondayWorking)) {
      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        Service.latestTimepoint(incorpUpdatesGoodOnlyRealTimepoints) mustBe previousGoodTimePoint.toString
        loggingEvents.size mustBe 0
      }
    }
    "don't throw a log message if working day true, timepoint = now" in new Setup(nDCalc(mondayWorking)) {
      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        Service.latestTimepoint(incorpUpdatesGoodOnlyRealTimepoints) mustBe previousGoodTimePoint.toString
        loggingEvents.size mustBe 0
      }
    }
    "return the latest timepoint when two have been given" in new Setup {
      val response = Service.latestTimepoint(incorpUpdatesGoodOnlyRealTimepoints)
      response mustBe previousGoodTimePoint.toString
    }
  }

  "updateNextIncorpUpdateJobLot" must {

    "return a string stating that states 'No Incorporation updates were fetched'" in new Setup {
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepoint.toString)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(Some(timepoint.toString))).thenReturn(Future.successful(emptyUpdates))
      when(mockIncorpUpdateRepository.storeIncorpUpdates(emptyUpdates)).thenReturn(Future(InsertResult(0, 0, Seq())))

      val response = await(Service.updateNextIncorpUpdateJobLot)
      response mustBe InsertResult(0, 0, Seq())

    }

    "return a string stating that the timepoint has been updated to 'new timepoint' (which is actually non parsable)" in new Setup {
      val newTimepoint = timepointNew
      when(mockSubscriptionService.getSubscription(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future(Some(subCT)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepointOld)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(ArgumentMatchers.eq(Some(timepointOld)))(ArgumentMatchers.any())).thenReturn(Future.successful(incorpUpdates))

      when(mockIncorpUpdateRepository.storeIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdates)))

      when(mockQueueRepository.storeIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      val captor = ArgumentCaptor.forClass(classOf[String])

      when(mockTimepointRepository.updateTimepoint(ArgumentMatchers.any())).thenReturn(Future.successful(newTimepoint))

      val response = await(Service.updateNextIncorpUpdateJobLot)
      verify(mockTimepointRepository).updateTimepoint(captor.capture())
      captor.getValue.toString mustBe newTimepoint
      response mustBe InsertResult(2, 0, Seq(), 0, incorpUpdates)
    }
    "return a string stating that the timepoint has been updated to '2016010101000'" in new Setup {
      val iup1 = IncorpUpdate("transIdNew", "accepted", None, None, "2016010101000", None)
      val iup2 = IncorpUpdate("transIdNew", "accepted", None, None, "201501010100", None)
      val incorpUpdatesvalidTP = Seq(iup2, iup1)
      when(mockSubscriptionService.getSubscription(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(Future(Some(subCT)))
      when(mockTimepointRepository.retrieveTimePoint).thenReturn(Future.successful(Some(timepointOld)))
      when(mockIncorporationCheckAPIConnector.checkForIncorpUpdate(ArgumentMatchers.eq(Some(timepointOld)))(ArgumentMatchers.any())).thenReturn(Future.successful(incorpUpdatesvalidTP))

      when(mockIncorpUpdateRepository.storeIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq(), 0, incorpUpdatesvalidTP)))

      when(mockQueueRepository.storeIncorpUpdates(ArgumentMatchers.any())).thenReturn(Future.successful(InsertResult(2, 0, Seq())))

      val captor = ArgumentCaptor.forClass(classOf[String])

      when(mockTimepointRepository.updateTimepoint(ArgumentMatchers.any())).thenReturn(Future.successful("2016010101000"))

      val response = await(Service.updateNextIncorpUpdateJobLot)
      verify(mockTimepointRepository).updateTimepoint(captor.capture())
      captor.getValue.toString mustBe "2016010101000"
      response mustBe InsertResult(2, 0, Seq(), 0, incorpUpdatesvalidTP)
    }
  }

  "updateSpecificIncorpUpdateByTP" must {

    "return a Sequence of trues when a sequence of TPs is input and there is a queue entry for each" in new Setup {

      val UWR = UpdateResult.acknowledged(1, 1, BsonString("newUpsertedId"))

      when(mockQueueRepository.removeQueuedIncorpUpdate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(queuedIncorpUpdate)))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(ArgumentMatchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(ArgumentMatchers.any()))
        .thenReturn(Future.successful(InsertResult(1, 0, Nil)))

      val response = await(Service.updateSpecificIncorpUpdateByTP(timepointSeq))
      response mustBe Seq(true, true)

    }
    "return a Sequence of false when a sequence of TPs is input and queue entries don't exist" in new Setup {

      val UWR = UpdateResult.acknowledged(1, 1, BsonString("newUpsertedId"))

      when(mockQueueRepository.removeQueuedIncorpUpdate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(ArgumentMatchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(ArgumentMatchers.any()))
        .thenReturn(Future.successful(InsertResult(1, 0, Nil)))

      val response = await(Service.updateSpecificIncorpUpdateByTP(timepointSeq))
      response mustBe Seq(false, false)

    }

    "return a Sequence of trues when a sequence of TPs is input and queue entries don't exist but the for no queue switch is set" in new Setup {

      val UWR = UpdateResult.acknowledged(1, 1, BsonString("newUpsertedId"))

      when(mockQueueRepository.removeQueuedIncorpUpdate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(true))

      when(mockQueueRepository.getIncorpUpdate(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      when(mockIncorpUpdateRepository.storeSingleIncorpUpdate(ArgumentMatchers.any())).thenReturn(Future.successful(UWR))

      when(mockIncorporationCheckAPIConnector.checkForIndividualIncorpUpdate(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Seq(incorpUpdate)), Future.successful(Seq(incorpUpdateNew)))

      when(mockQueueRepository.storeIncorpUpdates(ArgumentMatchers.any()))
        .thenReturn(Future.successful(InsertResult(1, 0, Nil)))

      val response = await(Service.updateSpecificIncorpUpdateByTP(timepointSeq, true))
      response mustBe Seq(true, true)

    }

  }

  "createQueuedIncorpUpdate" must {
    "return a correctly formatted QueuedIncorpUpdate when given an IncorpUpdate" in new Setup {

      val result = Service.createQueuedIncorpUpdates(Seq(incorpUpdate))

      result.head.copy(timestamp = queuedIncorpUpdate.timestamp) mustBe queuedIncorpUpdate
      result.head.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli mustBe (queuedIncorpUpdate.timestamp.toInstant(ZoneOffset.UTC).toEpochMilli +- 1500)
    }
  }

  "copyToQueue" must {
    "return true if a Seq of QueuedIncorpUpdates have been copied to the queue" in new Setup {
      when(mockQueueRepository.storeIncorpUpdates(Seq(queuedIncorpUpdate))).thenReturn(Future(InsertResult(1, 0, Seq())))

      val result = await(Service.copyToQueue(Seq(queuedIncorpUpdate)))
      result mustBe true
    }

    "return false if a Seq of QueuedIncorpUpdates have not been copied to the queue" in new Setup {
      when(mockQueueRepository.storeIncorpUpdates(Seq(queuedIncorpUpdate))).thenReturn(Future(InsertResult(0, 1, Seq())))

      val result = await(Service.copyToQueue(Seq(queuedIncorpUpdate)))
      result mustBe false
    }
  }

  "alertOnNoCTInterest" must {

    "return 0 and not raise an alert when there's an interest registered" in new Setup {
      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(Some(subCT)))

      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        val result = await(Service.alertOnNoCTInterest(seqOfIncorpUpdates))
        result mustBe 0

        loggingEvents.isEmpty mustBe true
      }
    }

    "return 2 where there were two incorps without an interest registered for regime ct or ctax" in new Setup(days = "FOO") {
      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(None))

      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCTAX), eqTo(subscriber)))
        .thenReturn(Future(None))

      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        val result = await(Service.alertOnNoCTInterest(incorpUpdates))
        result mustBe 2

        loggingEvents.size mustBe 2
      }
    }

    "return 1 alerts when 2 out of 3 incorps have an interested registered" in new Setup(days = "BAR") {
      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(Some(subCT)), Future(None), Future(Some(subCT)))

      when(mockSubscriptionService.getSubscription(any(), eqTo(regimeCTAX), eqTo(subscriber)))
        .thenReturn(Future(None))

      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        val result = await(Service.alertOnNoCTInterest(seqOfIncorpUpdates))
        result mustBe 1

        loggingEvents.size mustBe 1
      }
    }

    "return 0 when there is 1 incorp update with a ctax regime" in new Setup {
      when(mockSubscriptionService.getSubscription(eqTo(transId), eqTo(regimeCT), eqTo(subscriber)))
        .thenReturn(Future(None))

      when(mockSubscriptionService.getSubscription(eqTo(transId), eqTo(regimeCTAX), eqTo(subscriber)))
        .thenReturn(Future(Some(subCT)))

      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        val result = await(Service.alertOnNoCTInterest(Seq(incorpUpdate)))
        result mustBe 0

        loggingEvents.size mustBe 0
      }
    }

    "return 0 when there are 2 incorp updates; 1 with a ct regime sub and 1 with a ctax regime sub" in new Setup {
      when(mockSubscriptionService.getSubscription(any(), any(), eqTo(subscriber)))
        .thenReturn(Future.successful(Some(subCT)), Future.successful(None), Future.successful(Some(subCTAX)))

      withCaptureOfLoggingFrom(Service.logger) { loggingEvents =>
        val result = await(Service.alertOnNoCTInterest(Seq(incorpUpdate, incorpUpdate2)))
        result mustBe 0

        loggingEvents.size mustBe 0

        verify(mockSubscriptionService, times(1)).getSubscription(eqTo(transId), eqTo(regimeCT), eqTo(subscriber))
        verify(mockSubscriptionService, times(1)).getSubscription(eqTo(transId2), eqTo(regimeCTAX), eqTo(subscriber))
      }
    }

    "return 0 alerts if no incorps are processed" in new Setup {
      val result = await(Service.alertOnNoCTInterest(emptyUpdates))
      result mustBe 0
    }
  }
}
