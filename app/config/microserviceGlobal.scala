/*
 * Copyright 2019 HM Revenue & Customs
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

import com.typesafe.config.Config
import javax.inject.{Inject, Named, Singleton}
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration, Logger, Play}
import repositories.{IncorpUpdateMongo, QueueMongo, SubscriptionsMongo, TimepointMongo}
import services.{IncorpUpdateService, SubscriptionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.play.scheduling.{RunningOfScheduledJobs, ScheduledJob}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector

  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with MicroserviceFilterSupport with RunningOfScheduledJobs {
  override lazy val scheduledJobs = Play.current.injector.instanceOf[Jobs].lookupJobs()
  override val auditConnector = MicroserviceAuditConnector
  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = Some(MicroserviceAuthFilter)

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override def onStart(app : play.api.Application) : scala.Unit = {

    val confVersion = app.configuration.getString("config.version")
    Logger.info(s"[Config] Config Version = ${confVersion}")

    val metricsKey = "microservice.metrics.graphite.enabled"
    val metricsEnabled = app.configuration.getString(metricsKey)
    Logger.info(s"[Config] ${metricsKey} = ${metricsEnabled}")

    reFetchIncorpInfo(app)

    reFetchIncorpInfoWhenNoQueue(app)

    resetTimepoint(app)

    recreateSubscription(app)

    app.injector.instanceOf[AppStartupJobs].logIncorpInfo()
    app.injector.instanceOf[AppStartupJobs].logRemainingSubscriptionIdentifiers()

    super.onStart(app)
  }

  private def reFetchIncorpInfo(app: Application): Future[Unit] = {

    app.configuration.getString("timepointList") match {
      case None => Future.successful(Logger.info(s"[Config] No timepoints to re-fetch"))
      case Some(timepointList) =>
        val tpList = new String(Base64.getDecoder.decode(timepointList), "UTF-8")
        Logger.info(s"[Config] List of timepoints are $tpList")
        app.injector.instanceOf[IncorpUpdateService].updateSpecificIncorpUpdateByTP(tpList.split(","))(HeaderCarrier()) map { result =>
          Logger.info(s"Updating incorp data is switched on - result = $result")
        }
    }
  }

  private def recreateSubscription(app: Application): Future[Unit] = {
    app.configuration.getString("resubscribe") match {
      case None         => Future.successful(Logger.info(s"[Config] No re-subscriptions"))
      case Some(resubs) =>
        val configString = new String(Base64.getDecoder.decode(resubs), "UTF-8")
        configString.split(",").toList match {
          case txId :: callbackUrl :: Nil =>
            app.injector.instanceOf[SubscriptionService].checkForSubscription(txId, "ctax","scrs",callbackUrl,true)(HeaderCarrier()) map {
              result => Logger.info(s"[Config] result of subscription service call for $txId = $result")
            }
          case _ => Future.successful(Logger.info(s"[Config] No info in re-subscription variable"))
        }
    }

  }

  private def reFetchIncorpInfoWhenNoQueue(app: Application): Future[Unit] = {

    app.configuration.getString("timepointListNoQueue") match {
      case None => Future.successful(Logger.info(s"[Config] No timepoints to re-fetch for no queue entries"))
      case Some(timepointListNQ) =>
        val tpList = new String(Base64.getDecoder.decode(timepointListNQ), "UTF-8")
        Logger.info(s"[Config] List of timepoints for no queue entries are $tpList")
        app.injector.instanceOf[IncorpUpdateService].updateSpecificIncorpUpdateByTP(tpList.split(","), forNoQueue = true)(HeaderCarrier()) map { result =>
          Logger.info(s"Updating incorp data is switched on for no queue entries - result = $result")
        }
    }
  }

  private def resetTimepoint(app: Application): Future[Boolean] = {
    implicit val ex: ExecutionContext = app.materializer.executionContext.prepare()

    app.configuration.getString("microservice.services.reset-timepoint-to").fold{
      Logger.info("[ResetTimepoint] Could not find a timepoint to reset to (config key microservice.services.reset-timepoint-to)")
      Future(false)
    }{
      timepoint =>
        Logger.info(s"[ResetTimepoint] Found timepoint from config - $timepoint")
        app.injector.instanceOf[TimepointMongo].repo.resetTimepointTo(timepoint)
    }
  }
}

@Singleton
class AppStartupJobs @Inject()(config: Configuration,
                               val subsRepo: SubscriptionsMongo,
                               val incorpUpdateRepo: IncorpUpdateMongo,
                               val queueRepo: QueueMongo) {

  def logIncorpInfo(): Unit = {
    val transIdsFromConfig = config.getString("transactionIdList")

    transIdsFromConfig.fold(()){ transIds =>
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
    val regime = config.getString("log-regime").getOrElse("ct")
    val maxAmountToLog = config.getInt("log-count").getOrElse(20)

    subsRepo.repo.getSubscriptionsByRegime(regime, maxAmountToLog) map { subs =>
      Logger.info(s"Logging existing subscriptions for $regime regime, found ${subs.size} subscriptions")
      subs foreach { sub =>
        Logger.info(s"[Subscription] [$regime] Transaction ID: ${sub.transactionId}, Subscriber: ${sub.subscriber}")
      }
    }
  }
}






trait JobsList {
  def lookupJobs(): Seq[ScheduledJob] = Seq()
}

@Singleton
class Jobs @Inject()(@Named("incorp-update-job") injIncUpdJob: ScheduledJob,
                     @Named("fire-subs-job") injFireSubsJob: ScheduledJob,
                     @Named("metrics-job") injMetricsJob: ScheduledJob,
                     @Named("proactive-monitoring-job") injProMonitoringJob: ScheduledJob
                    ) extends JobsList {
  override def lookupJobs(): Seq[ScheduledJob] =
    Seq(
      injIncUpdJob,
      injFireSubsJob,
      injMetricsJob,
      injProMonitoringJob
    )
}
