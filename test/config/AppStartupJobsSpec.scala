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
import com.mongodb.client.result.DeleteResult
import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import play.api.test.DefaultAwaitTimeout
import play.api.{Configuration, Logger}
import repositories._
import services.{IncorpUpdateService, SubscriptionService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AppStartupJobsSpec extends SCRSSpec with LogCapturing with Eventually with DefaultAwaitTimeout{


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
      mockSubsMongo, mockSubsRepo,
      mockIncorpUpdateMongo, mockIncorpUpdateRepo,
      mockQueueMongo, mockQueueRepo, mockIIService, mocksubService
    )
  }

  class Setup {
    resetMocks()

    def appStartupJobs(config: Configuration): StartUpJobs = new StartUpJobs(
      config,
      mockIIService,
      mocksubService,
      mocktpRepo,
      mockSubsMongo,
      mockIncorpUpdateMongo,
      mockQueueMongo
    )

  }

  "logRemainingSubscriptionIdentifiers" must {

    "log information about subscriptions with default values as nothing exists in config" in new Setup {
      val subscription1 = Subscription("transId", "testRegime", "testSubscriber", "testCallbackUrl")
      val subscriptions: Seq[Subscription] = Seq.fill(5)(subscription1)
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)
      when(mockIIService.updateSpecificIncorpUpdateByTP(any(), any())(any(), any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptions(any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptionsByRegime(eqTo("ct"), eqTo(20))).thenReturn(Future.successful(subscriptions))

      val expectedLogOfSubscriptions = {
        List("Logging existing subscriptions for ct regime, found 5 subscriptions") ++
          List.fill(5)("[Subscription] [ct] Transaction ID: transId, Subscriber: testSubscriber")
      }

      withCaptureOfLoggingFrom(Logger(appStartupJobs(Configuration()).getClass)) { logEvents =>
        appStartupJobs(Configuration())
        eventually {
          logEvents.map(_.getMessage).count(r => expectedLogOfSubscriptions.contains(r)) mustBe 6
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

      withCaptureOfLoggingFrom(Logger(appStartupJobs(Configuration()).getClass)) { logEvents =>
        appStartupJobs(Configuration())
        eventually {

          val expectedLogs = List(
            "Logging existing subscriptions for ct regime, found 0 subscriptions"
          )
          logEvents.map(_.getMessage).count(r => expectedLogs.contains(r)) mustBe 1
        }
      }
    }
  }

  "logIncorpInfo" must {

    val dateTime = LocalDateTime.of(2010, 6, 30, 1, 20, 0)

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
      when(mockSubsRepo.getSubscriptions(eqTo(transId1))).thenReturn(Future.successful(Seq(subscription1)))
      when(mockSubsRepo.getSubscriptions(eqTo(transId2))).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())).thenReturn(Future.successful(Seq()))

      when(mockIncorpUpdateRepo.getIncorpUpdate(eqTo(transId1)))
        .thenReturn(Future.successful(Some(incorpUpdate1)))
      when(mockIncorpUpdateRepo.getIncorpUpdate(eqTo(transId2)))
        .thenReturn(Future.successful(None))

      when(mockQueueRepo.getIncorpUpdate(eqTo(transId1)))
        .thenReturn(Future.successful(Some(queuedUpdate)))
      when(mockQueueRepo.getIncorpUpdate(eqTo(transId2)))
        .thenReturn(Future.successful(None))

      val expectedLogs = List(
        "[HeldDocs] For txId: trans-1 - subscriptions: List(Subscription(trans-1,testRegime,testSubscriber,testCallbackUrl)) - " +
          "incorp update: incorp status: accepted - incorp date: Some(2010-06-30T01:20:00.0) - crn: Some(crn-1) - timepoint: 12345 - queue: In queue",
        "[HeldDocs] For txId: trans-2 - subscriptions: No subs - incorp update: No incorp update - queue: No queued incorp update"
      )

      withCaptureOfLoggingFrom(Logger(appStartupJobs(Configuration()).getClass)) { logEvents =>

        appStartupJobs(Configuration.from(Map("transactionIdList" -> "dHJhbnMtMSx0cmFucy0y")))
        eventually {
          logEvents.map(_.getMessage).count(expectedLogs.contains(_)) mustBe 2
        }
      }
    }

    "not log anything is an encoded transaction ID list is not provided in config" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)
      when(mockIIService.updateSpecificIncorpUpdateByTP(any(), any())(any(), any())).thenReturn(Future.successful(Seq()))
      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())).thenReturn(Future.successful(Seq()))
      withCaptureOfLoggingFrom(Logger(appStartupJobs(Configuration()).getClass)) { logEvents =>
        appStartupJobs(Configuration())
        eventually {
          logEvents.map(_.getMessage).filter(_.contains("[HeldDocs]")) mustBe empty
        }
      }
    }
  }

  "removeBrokenSubmissions" must {

    val encodedTransIds = "dHJhbnMtMSx0cmFucy0y"

    val transId1 = "trans-1"
    val transId2 = "trans-2"

    val deleteResult = DeleteResult.acknowledged(1)

    "log when broken submissions are removed" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)

      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())) thenReturn Future.successful(Seq())


      when(mockSubsRepo.deleteSub(eqTo(transId1),eqTo("ctax"),eqTo("scrs")))
        .thenReturn(Future.successful(deleteResult))
      when(mockSubsRepo.deleteSub(eqTo(transId2),eqTo("ctax"),eqTo("scrs")))
        .thenReturn(Future.successful(deleteResult))


      when(mockQueueRepo.removeQueuedIncorpUpdate(eqTo(transId1)))
        .thenReturn(Future.successful(true))
      when(mockQueueRepo.removeQueuedIncorpUpdate(eqTo(transId2)))
        .thenReturn(Future.successful(true))


      withCaptureOfLoggingFrom(Logger(appStartupJobs(Configuration()).getClass)) { logEvents =>
        appStartupJobs(Configuration.from(Map("brokenTxIds" -> encodedTransIds)))

        eventually {
          val expectedLogs = List(
              "[Start Up] Removed broken submission with txId: trans-1 - delete sub: AcknowledgedDeleteResult{deletedCount=1} queue result: true",
              "[Start Up] Removed broken submission with txId: trans-2 - delete sub: AcknowledgedDeleteResult{deletedCount=1} queue result: true"
          )
          logEvents.map(_.getMessage).count(expectedLogs.contains(_)) mustBe 2
        }
      }
    }

    "log when no broken submissions are supplied" in new Setup {
      when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
      when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
      when(mockQueueMongo.repo).thenReturn(mockQueueRepo)

      when(mockSubsRepo.getSubscriptionsByRegime(any(), any())) thenReturn Future.successful(Seq())

      withCaptureOfLoggingFrom(Logger(appStartupJobs(Configuration()).getClass)) { logEvents =>
        appStartupJobs(Configuration.from(Map()))

        eventually {
          val expectedLogs = List(
            "[Start Up] No broken submissions in config"
          )
          logEvents.map(_.getMessage).count(expectedLogs.contains(_)) mustBe 1
        }
      }
    }
  }
}