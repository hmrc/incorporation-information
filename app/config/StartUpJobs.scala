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

package config

import java.util.Base64

import javax.inject.Inject
import play.api.{Configuration, Logger}
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo, TimepointMongo}
import services.{IncorpUpdateService, SubscriptionService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class StartUpJobsImpl @Inject()(val configuration: Configuration,
                                val incorpUpdateService: IncorpUpdateService,
                                val subscriptionService: SubscriptionService,
                                val timepointMongo: TimepointMongo,
                                val subsRepo: SubscriptionsMongo,
                                val incorpUpdateRepo: IncorpUpdateMongo,
                                val queueRepo: QueueMongo
                               ) extends StartUpJobs

trait StartUpJobs {
  val configuration: Configuration
  val incorpUpdateService: IncorpUpdateService
  val subscriptionService: SubscriptionService
  val timepointMongo: TimepointMongo
  val subsRepo: SubscriptionsMongo
  val incorpUpdateRepo: IncorpUpdateMongo
  val queueRepo: QueueMongo

  lazy val tpConfig = configuration.getString("timepointList")

  private def reFetchIncorpInfo(): Future[Unit] = {
    tpConfig match {
      case None => Future.successful(Logger.info(s"[Config] No timepoints to re-fetch"))
      case Some(timepointList) =>
        val tpList = new String(Base64.getDecoder.decode(timepointList), "UTF-8")
        Logger.info(s"[Config] List of timepoints are $tpList")
        incorpUpdateService.updateSpecificIncorpUpdateByTP(tpList.split(","))(HeaderCarrier()) map { result =>
          Logger.info(s"Updating incorp data is switched on - result = $result")
        }
    }
  }

  private def recreateSubscription(): Future[Unit] = {
    configuration.getString("resubscribe") match {
      case None => Future.successful(Logger.info(s"[Config] No re-subscriptions"))
      case Some(resubs) =>
        val configString = new String(Base64.getDecoder.decode(resubs), "UTF-8")
        configString.split(",").toList match {
          case txId :: callbackUrl :: Nil =>
            subscriptionService.checkForSubscription(txId, "ctax", "scrs", callbackUrl, true)(HeaderCarrier()) map {
              result => Logger.info(s"[Config] result of subscription service call for $txId = $result")
            }
          case _ => Future.successful(Logger.info(s"[Config] No info in re-subscription variable"))
        }
    }
  }

  private def reFetchIncorpInfoWhenNoQueue(): Future[Unit] = {

    configuration.getString("timepointListNoQueue") match {
      case None => Future.successful(Logger.info(s"[Config] No timepoints to re-fetch for no queue entries"))
      case Some(timepointListNQ) =>
        val tpList = new String(Base64.getDecoder.decode(timepointListNQ), "UTF-8")
        Logger.info(s"[Config] List of timepoints for no queue entries are $tpList")
        incorpUpdateService.updateSpecificIncorpUpdateByTP(tpList.split(","), forNoQueue = true)(HeaderCarrier()) map { result =>
          Logger.info(s"Updating incorp data is switched on for no queue entries - result = $result")
        }
    }
  }

  def logIncorpInfo(): Unit = {
    val transIdsFromConfig = configuration.getString("transactionIdList")
    transIdsFromConfig.fold(()) { transIds =>
      val transIdList = utils.Base64.decode(transIds).split(",")
      transIdList.foreach { transId =>
        for {
          subscriptions <- subsRepo.repo.getSubscriptions(transId)
          incorpUpdate <- incorpUpdateRepo.repo.getIncorpUpdate(transId)
          queuedUpdate <- queueRepo.repo.getIncorpUpdate(transId)
        } yield {
          Logger.info(s"[HeldDocs] For txId: $transId - " +
            s"subscriptions: ${if (subscriptions.isEmpty) "No subs" else subscriptions} - " +
            s"""incorp update: ${
              incorpUpdate.fold("No incorp update")(incorp =>
                s"incorp status: ${incorp.status} - " +
                  s"incorp date: ${incorp.incorpDate} - " +
                  s"crn: ${incorp.crn} - " +
                  s"timepoint: ${incorp.timepoint}"
              )
            } - """ +
            s"""queue: ${queuedUpdate.fold("No queued incorp update")(_ => "In queue")}""")
        }
      }
    }
  }

  def logRemainingSubscriptionIdentifiers(): Unit = {
    val regime = configuration.getString("log-regime").getOrElse("ct")
    val maxAmountToLog = configuration.getInt("log-count").getOrElse(20)

    subsRepo.repo.getSubscriptionsByRegime(regime, maxAmountToLog) map { subs =>
      Logger.info(s"Logging existing subscriptions for $regime regime, found ${subs.size} subscriptions")
      subs foreach { sub =>
        Logger.info(s"[Subscription] [$regime] Transaction ID: ${sub.transactionId}, Subscriber: ${sub.subscriber}")
      }
    }
  }

  private def resetTimepoint(): Future[Boolean] = {
    configuration.getString("microservice.services.reset-timepoint-to").fold {
      Logger.info("[ResetTimepoint] Could not find a timepoint to reset to (config key microservice.services.reset-timepoint-to)")
      Future(false)
    } {
      timepoint =>
        Logger.info(s"[ResetTimepoint] Found timepoint from config - $timepoint")
        timepointMongo.repo.resetTimepointTo(timepoint)
    }
  }

  reFetchIncorpInfo()

  reFetchIncorpInfoWhenNoQueue()

  resetTimepoint()

  recreateSubscription()

  logIncorpInfo()

  logRemainingSubscriptionIdentifiers()

}