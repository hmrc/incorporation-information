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

package config

import org.apache.pekko.actor.ActorSystem
import com.google.inject.Singleton
import play.api.Configuration
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo, TimepointMongo}
import services.{IncorpUpdateService, SubscriptionService}
import uk.gov.hmrc.http.HeaderCarrier
import utils.{Logging, TimestampFormats}

import java.util.Base64
import javax.inject.Inject
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StartUpJobs @Inject()(val configuration: Configuration,
                            val incorpUpdateService: IncorpUpdateService,
                            val subscriptionService: SubscriptionService,
                            val timepointMongo: TimepointMongo,
                            val subsRepo: SubscriptionsMongo,
                            val incorpUpdateRepo: IncorpUpdateMongo,
                            val queueRepo: QueueMongo,
                            val appConfig: MicroserviceConfig
                           )(implicit val ec: ExecutionContext) extends Logging {

  lazy val jobName = "startUpJob"
  lazy val actor: ActorSystem = ActorSystem(jobName)
  lazy val initialDelayMillis = appConfig.getConfigInt(s"$jobName.initialDelayMillis")

  lazy val tpConfig = configuration.getOptional[String]("timepointList")

  private def reFetchIncorpInfo(): Future[Unit] = {
    tpConfig match {
      case None => Future.successful(logger.info(s"[reFetchIncorpInfo] No timepoints to re-fetch"))
      case Some(timepointList) =>
        val tpList = new String(Base64.getDecoder.decode(timepointList), "UTF-8")
        logger.info(s"[reFetchIncorpInfo] List of timepoints are $tpList")
        incorpUpdateService.updateSpecificIncorpUpdateByTP(tpList.split(","))(HeaderCarrier(), ec) map { result =>
          logger.info(s"[reFetchIncorpInfo] Updating incorp data is switched on - result = $result")
        }
    }
  }

  private def recreateSubscription(): Future[Unit] = {
    configuration.getOptional[String]("resubscribe") match {
      case None => Future.successful(logger.info(s"[recreateSubscription] No re-subscriptions"))
      case Some(resubs) =>
        val configString = new String(Base64.getDecoder.decode(resubs), "UTF-8")
        configString.split(",").toList match {
          case txId :: callbackUrl :: Nil =>
            subscriptionService.checkForSubscription(txId, "ctax", "scrs", callbackUrl, true) map {
              result => logger.info(s"[recreateSubscription] result of subscription service call for $txId = $result")
            }
          case _ => Future.successful(logger.info(s"[recreateSubscription] No info in re-subscription variable"))
        }
    }
  }

  private def reFetchIncorpInfoWhenNoQueue(): Future[Unit] = {

    configuration.getOptional[String]("timepointListNoQueue") match {
      case None => Future.successful(logger.info(s"[reFetchIncorpInfoWhenNoQueue] No timepoints to re-fetch for no queue entries"))
      case Some(timepointListNQ) =>
        val tpList = new String(Base64.getDecoder.decode(timepointListNQ), "UTF-8")
        logger.info(s"[reFetchIncorpInfoWhenNoQueue] List of timepoints for no queue entries are $tpList")
        incorpUpdateService.updateSpecificIncorpUpdateByTP(tpList.split(","), forNoQueue = true)(HeaderCarrier(), ec) map { result =>
          logger.info(s"[reFetchIncorpInfoWhenNoQueue] Updating incorp data is switched on for no queue entries - result = $result")
        }
    }
  }

  private def logIncorpInfo(): Unit = {
    val transIdsFromConfig = configuration.getOptional[String]("transactionIdList")
    transIdsFromConfig.fold(()) { transIds =>
      val transIdList = utils.Base64.decode(transIds).split(",")
      transIdList.foreach { transId =>
        for {
          subscriptions <- subsRepo.repo.getSubscriptions(transId)
          incorpUpdate <- incorpUpdateRepo.repo.getIncorpUpdate(transId)
          queuedUpdate <- queueRepo.repo.getIncorpUpdate(transId)
        } yield {
          logger.info(s"[logIncorpInfo][HeldDocs] For txId: $transId - " +
            s"subscriptions: ${if (subscriptions.isEmpty) "No subs" else subscriptions} - " +
            s"""incorp update: ${
              incorpUpdate.fold("No incorp update")(incorp =>
                s"incorp status: ${incorp.status} - " +
                  s"incorp date: ${incorp.incorpDate.map(TimestampFormats.ldtFormatter.format(_))} - " +
                  s"crn: ${incorp.crn} - " +
                  s"timepoint: ${incorp.timepoint}"
              )
            } - """ +
            s"""queue: ${queuedUpdate.fold("No queued incorp update")(_ => "In queue")}""")
        }
      }
    }
  }

  private def logRemainingSubscriptionIdentifiers(): Unit = {
    val regime = configuration.getOptional[String]("log-regime").getOrElse("ct")
    val maxAmountToLog = configuration.getOptional[Int]("log-count").getOrElse(20)

    subsRepo.repo.getSubscriptionsByRegime(regime, maxAmountToLog) map { subs =>
      logger.info(s"[logRemainingSubscriptionIdentifiers] Logging existing subscriptions for $regime regime, found ${subs.size} subscriptions")
      subs foreach { sub =>
        logger.info(s"[logRemainingSubscriptionIdentifiers][$regime] Transaction ID: ${sub.transactionId}, Subscriber: ${sub.subscriber}")
      }
    }
  }

  private def resetTimepoint(): Future[Boolean] = {
    configuration.getOptional[String]("microservice.services.reset-timepoint-to").fold {
      logger.info("[resetTimepoint] Could not find a timepoint to reset to (config key microservice.services.reset-timepoint-to)")
      Future(false)
    } {
      timepoint =>
        logger.info(s"[resetTimepoint] Found timepoint from config - $timepoint")
        timepointMongo.repo.updateTimepoint(timepoint).map(_ => true)
    }
  }

  private def removeBrokenSubmissions(): Unit = {
    val transIdsFromConfig = configuration.getOptional[String]("brokenTxIds")
    transIdsFromConfig.fold(
      logger.info(s"[removeBrokenSubmissions] No broken submissions in config")
    ) { transIds =>
      val transIdList = utils.Base64.decode(transIds).split(",")
      transIdList.foreach { transId =>
        for {
          writeResult <- subsRepo.repo.deleteSub(transId,"ctax","scrs")
          queueResult <- queueRepo.repo.removeQueuedIncorpUpdate(transId)
        } yield {
          logger.info(s"[removeBrokenSubmissions] Removed broken submission with txId: $transId - delete sub: $writeResult queue result: $queueResult")
        }
      }
    }
  }

  actor.scheduler.scheduleOnce(initialDelayMillis.milliseconds) {
    reFetchIncorpInfo()
    reFetchIncorpInfoWhenNoQueue()
    resetTimepoint()
    recreateSubscription()
    logIncorpInfo()
    logRemainingSubscriptionIdentifiers()
    removeBrokenSubmissions()
  }
}
