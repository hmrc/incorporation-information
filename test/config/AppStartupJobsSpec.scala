/*
 * Copyright 2022 HM Revenue & Customs
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

package config

import Helpers.{LogCapturing, SCRSSpec}
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import play.api.{Configuration, Logger}
import repositories._
import services.{IncorpUpdateService, SubscriptionService}
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AppStartupJobsSpec extends SCRSSpec with LogCapturing with Eventually {

  val mockConfig: Configuration = mock[Configuration]

  val mockSubsMongo: SubscriptionsMongo = mock[SubscriptionsMongo]
  val mockSubsRepo: SubscriptionsMongoRepository = mock[SubscriptionsMongoRepository]
  val mockIIService = mock[IncorpUpdateService]
  val mocksubService = mock[SubscriptionService]
  val mocktpRepo = mock[TimepointMongo]

  val mockIncorpUpdateMongo: IncorpUpdateMongo = mock[IncorpUpdateMongo]
  val mockIncorpUpdateRepo: IncorpUpdateMongoRepository = mock[IncorpUpdateMongoRepository]

  val mockQueueMongo: QueueMongo = mock[QueueMongo]
  val mockQueueRepo: QueueMongoRepository = mock[QueueMongoRepository]

  implicit val hc = HeaderCarrier()

  def resetMocks() {
    reset(
      mockConfig, mockSubsMongo, mockSubsRepo,
      mockIncorpUpdateMongo, mockIncorpUpdateRepo,
      mockQueueMongo, mockQueueRepo, mockIIService, mocksubService
    )
  }

  class Setup {
    val loggerId: String = UUID.randomUUID().toString

    val testLogger: Logger = Logger(loggerId)

    def appStartupJobs(config: Configuration): StartUpJobs = new StartUpJobs(
      config,
      mockIIService,
      mocksubService,
      mocktpRepo,
      mockSubsMongo,
      mockIncorpUpdateMongo,
      mockQueueMongo,
      testLogger
    )

    resetMocks()
  }

  "logIncorpInfo" should {

    //"trans-1,trans-2"
    //val encodedTransIds = "dHJhbnMtMSx0cmFucy0y"

    val dateTime = DateTime.parse("2010-06-30T01:20+00:00")

    val transId1 = "trans-1"
    val transId2 = "trans-2"

    val subscription1 = Subscription(transId1, "testRegime", "testSubscriber", "testCallbackUrl")

    val incorpUpdate1 = IncorpUpdate(transId1, "accepted", Some("crn-1"), Some(dateTime), "12345", None)

    val queuedUpdate = QueuedIncorpUpdate(dateTime, incorpUpdate1)

    "log specific incorporation information relating to each transaction ID found in config" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)
      when(mockIIService.updateSpecificIncorpUpdateByTP(any(), any())(any(), any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptions(eqTo(transId1)))
        .thenReturn(Future.successful(Seq(subscription1)))
      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())).thenReturn(Future.successful(Seq(subscription1)))
      when(mockSubsRepo.getSubscriptions(eqTo(transId2)))
        .thenReturn(Future.successful(Seq()))

      when(mockIncorpUpdateRepo.getIncorpUpdate(eqTo(transId1)))
        .thenReturn(Future.successful(Some(incorpUpdate1)))
      when(mockIncorpUpdateRepo.getIncorpUpdate(eqTo(transId2)))
        .thenReturn(Future.successful(None))

      when(mockQueueRepo.getIncorpUpdate(eqTo(transId1)))
        .thenReturn(Future.successful(Some(queuedUpdate)))
      when(mockQueueRepo.getIncorpUpdate(eqTo(transId2)))
        .thenReturn(Future.successful(None))

      withCaptureOfLoggingFrom(testLogger) { logEvents =>
        appStartupJobs(Configuration.from(Map("transactionIdList" -> "dHJhbnMtMSx0cmFucy0y", "timepointList" -> "MTIzNDU=")))

        eventually {
          val expectedLogs = List(
            "[HeldDocs] For txId: trans-1 - subscriptions: List(Subscription(trans-1,testRegime,testSubscriber,testCallbackUrl)) - " +
              "incorp update: incorp status: accepted - incorp date: Some(2010-06-30T01:20:00.000Z) - crn: Some(crn-1) - timepoint: 12345 - queue: In queue",
            "[HeldDocs] For txId: trans-2 - subscriptions: No subs - incorp update: No incorp update - queue: No queued incorp update"
          )
          logEvents.map(_.getMessage).count(expectedLogs.contains(_)) shouldBe 2
        }
      }
    }

    "not log anything is an encoded transaction ID list is not provided in config" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)
      when(mockIIService.updateSpecificIncorpUpdateByTP(any(), any())(any(), any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())).thenReturn(Future.successful(Seq()))
      withCaptureOfLoggingFrom(testLogger) { logEvents =>
        appStartupJobs(Configuration.from(Map("timepointList" -> "MTIzNDU=")))
        eventually {
          logEvents.map(_.getMessage).filter(_.contains("[HeldDocs]")) shouldBe empty
        }
      }
    }
  }

  "logRemainingSubscriptionIdentifiers" should {

    val subscription1 = Subscription("transId", "testRegime", "testSubscriber", "testCallbackUrl")

    "log information about subscriptions with default values as nothing exists in config" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)
      when(mockIIService.updateSpecificIncorpUpdateByTP(any(), any())(any(), any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptions(any()))
        .thenReturn(Future.successful(Seq(subscription1)))
      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())).thenReturn(Future.successful(Seq(subscription1)))

      val subscriptions = List.fill(5)(subscription1)
      val expectedLogOfSubscriptions = {
        List("Logging existing subscriptions for ct regime, found 5 subscriptions") ++
          List.fill(5)("[Subscription] [ct] Transaction ID: transId, Subscriber: testSubscriber")
      }
      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())) thenReturn Future.successful(subscriptions)

      withCaptureOfLoggingFrom(testLogger) { logEvents =>
        appStartupJobs(Configuration.from(Map("transactionIdList" -> "dHJhbnMtMSx0cmFucy0y", "timepointList" -> "MTIzNDU=")))

        eventually {
          logEvents.map(_.getMessage).count(r => expectedLogOfSubscriptions.contains(r)) shouldBe 6
        }
      }
    }

    "log the job ran but retrieved nothing when there are no subscriptions" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)
      when(mockIIService.updateSpecificIncorpUpdateByTP(any(), any())(any(), any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptions(any()))
        .thenReturn(Future.successful(Seq()))

      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())) thenReturn Future.successful(Seq())

      withCaptureOfLoggingFrom(testLogger) { logEvents =>
        appStartupJobs(Configuration.from(Map("transactionIdList" -> "dHJhbnMtMSx0cmFucy0y", "timepointList" -> "MTIzNDU=")))

        eventually {

          val expectedLogs = List(
            "Logging existing subscriptions for ct regime, found 0 subscriptions"
          )
          logEvents.map(_.getMessage).count(r => expectedLogs.contains(r)) shouldBe 1
        }
      }
    }
  }
}