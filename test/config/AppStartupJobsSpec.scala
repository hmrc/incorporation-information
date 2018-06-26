/*
 * Copyright 2018 HM Revenue & Customs
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

import models.{IncorpUpdate, QueuedIncorpUpdate, Subscription}
import org.joda.time.DateTime
import org.scalatest.mock.MockitoSugar
import play.api.{Configuration, Logger}
import repositories._
import uk.gov.hmrc.play.test.{LogCapturing, UnitSpec}
import org.mockito.Mockito._
import org.mockito.Matchers.{any, eq => eqTo}
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

class AppStartupJobsSpec extends UnitSpec with MockitoSugar with LogCapturing with Eventually {

  val mockConfig: Configuration = mock[Configuration]

  val mockSubsMongo: SubscriptionsMongo = mock[SubscriptionsMongo]
  val mockSubsRepo: SubscriptionsMongoRepository = mock[SubscriptionsMongoRepository]

  val mockIncorpUpdateMongo: IncorpUpdateMongo = mock[IncorpUpdateMongo]
  val mockIncorpUpdateRepo: IncorpUpdateMongoRepository = mock[IncorpUpdateMongoRepository]

  val mockQueueMongo: QueueMongo = mock[QueueMongo]
  val mockQueueRepo: QueueMongoRepository = mock[QueueMongoRepository]

  trait Setup {

    reset(
      mockConfig, mockSubsMongo, mockSubsRepo,
      mockIncorpUpdateMongo, mockIncorpUpdateRepo,
      mockQueueMongo, mockQueueRepo
    )

    val appStartupJobs = new AppStartupJobs(
      mockConfig,
      mockSubsMongo,
      mockIncorpUpdateMongo,
      mockQueueMongo
    )

    when(mockSubsMongo.repo).thenReturn(mockSubsRepo)
    when(mockIncorpUpdateMongo.repo).thenReturn(mockIncorpUpdateRepo)
    when(mockQueueMongo.repo).thenReturn(mockQueueRepo)

  }

  "logIncorpInfo" should {

    //"trans-1,trans-2"
    val encodedTransIds = "dHJhbnMtMSx0cmFucy0y"

    val dateTime = DateTime.parse("2010-06-30T01:20+00:00")

    val transId1 = "trans-1"
    val transId2 = "trans-2"

    val subscription1 = Subscription(transId1, "testRegime", "testSubscriber", "testCallbackUrl")

    val incorpUpdate1 = IncorpUpdate(transId1, "accepted", Some("crn-1"), Some(dateTime), "12345", None)

    val queuedUpdate = QueuedIncorpUpdate(dateTime, incorpUpdate1)

    "log specific incorporation information relating to each transaction ID found in config" in new Setup {

      when(mockConfig.getString(eqTo("transactionIdList"), any()))
        .thenReturn(Some(encodedTransIds))

      when(mockSubsRepo.getSubscriptions(eqTo(transId1)))
        .thenReturn(Future.successful(Seq(subscription1)))
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

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        appStartupJobs.logIncorpInfo()

        eventually {
          logEvents.size shouldBe 2

          val expectedLogs = List(
            "[HeldDocs] For txId: trans-1 - subscriptions: List(Subscription(trans-1,testRegime,testSubscriber,testCallbackUrl)) - " +
              "incorp update: incorp status: accepted - incorp date: Some(2010-06-30T01:20:00.000Z) - crn: Some(crn-1) - timepoint: 12345 - queue: In queue",
            "[HeldDocs] For txId: trans-2 - subscriptions: No subs - incorp update: No incorp update - queue: No queued incorp update"
          )

          logEvents.map(_.getMessage) should contain theSameElementsAs expectedLogs
        }
      }
    }

    "not log anything is an encoded transaction ID list is not provided in config" in new Setup {

      when(mockConfig.getString(eqTo("transactionIdList"), any()))
        .thenReturn(None)

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        appStartupJobs.logIncorpInfo()
        logEvents.size shouldBe 0
      }
    }
  }

  "logRemainingSubscriptionIdentifiers" should {
    val subscription1 = Subscription("transId", "testRegime", "testSubscriber", "testCallbackUrl")

    "log information about subscriptions" in new Setup {
      when(mockConfig.getString(eqTo("log-regime"), any()))
        .thenReturn(None)

      when(mockSubsRepo.getSubscriptionsByRegime(any(), any()))
        .thenReturn(Future.successful(Seq(subscription1)))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        appStartupJobs.logRemainingSubscriptionIdentifiers()

        eventually {
          logEvents.size shouldBe 2

          val expectedLogs = List(
            "Logging existing subscriptions for ct regime, found 1 subscriptions",
            "[Subscription] [ct] Transaction ID: transId, Subscriber: testSubscriber"
          )

          logEvents.map(_.getMessage) should contain theSameElementsAs expectedLogs
        }
      }
    }

    "log the job ran but retrieved nothing when there are no subscriptions" in new Setup {
      when(mockConfig.getString(eqTo("log-regime"), any()))
        .thenReturn(None)

      when(mockSubsRepo.getSubscriptionsByRegime(any(), any()))
        .thenReturn(Future.successful(Seq()))

      withCaptureOfLoggingFrom(Logger) { logEvents =>
        appStartupJobs.logRemainingSubscriptionIdentifiers()

        eventually {
          logEvents.size shouldBe 1

          val expectedLogs = List(
            "Logging existing subscriptions for ct regime, found 0 subscriptions"
          )

          logEvents.map(_.getMessage) should contain theSameElementsAs expectedLogs
        }
      }
    }
  }

}