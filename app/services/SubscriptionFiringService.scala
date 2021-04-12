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

import config.MicroserviceConfig
import connectors.FiringSubscriptionsConnector
import javax.inject.Inject
import jobs._
import models.{IncorpUpdateResponse, QueuedIncorpUpdate}
import org.joda.time.{DateTime, Duration}
import play.api.{Environment, Logger}
import reactivemongo.api.commands.{DefaultWriteResult, LastError}
import repositories._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockKeeper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class SubscriptionFiringServiceImpl @Inject()(fsConnector: FiringSubscriptionsConnector,
                                              injQueueRepo: QueueMongo,
                                              injSubRepo: SubscriptionsMongo,
                                              msConfig: MicroserviceConfig,
                                              val env: Environment,
                                              lockRepository: LockRepositoryProvider
                                             ) extends SubscriptionFiringService {
  override lazy val firingSubsConnector = fsConnector
  override lazy val queueRepository = injQueueRepo.repo
  override lazy val subscriptionsRepository = injSubRepo.repo
  override lazy val queueFailureDelay = msConfig.queueFailureDelay
  override lazy val queueRetryDelay = msConfig.queueRetryDelay
  override lazy val fetchSize = msConfig.queueFetchSize
  override lazy val useHttpsFireSubs = msConfig.useHttpsFireSubs
  lazy val lockoutTimeout = msConfig.getConfigInt("schedules.fire-subs-job.lockTimeout")

  lazy val lockKeeper: LockKeeper = new LockKeeper() {
    override val lockId = "fire-subs-job-lock"
    override val forceLockReleaseAfter: Duration = Duration.standardSeconds(lockoutTimeout)
    override lazy val repo = lockRepository.repo
  }
  implicit val hc = HeaderCarrier()
}

trait SubscriptionFiringService extends ScheduledService[Either[Seq[Boolean], LockResponse]] {
  val firingSubsConnector: FiringSubscriptionsConnector
  val lockKeeper: LockKeeper
  val queueRepository: QueueRepository
  val subscriptionsRepository: SubscriptionsRepository
  val queueFailureDelay: Int
  val queueRetryDelay: Int
  val fetchSize: Int
  val useHttpsFireSubs: Boolean

  implicit val hc: HeaderCarrier

def invoke(implicit ec: ExecutionContext): Future[Either[Seq[Boolean], LockResponse]] = {
  lockKeeper.tryLock(fireIncorpUpdateBatch).map {
    case Some(res) =>
      Logger.info("SubscriptionFiringService acquired lock and returned results")
      Logger.info(s"Result: $res")
      Left(res)
    case None =>
      Logger.info("SubscriptionFiringService cant acquire lock")
      Right(MongoLocked)
  }.recover {
    case e: Exception => Logger.error(s"Error running fireIncorpUpdateBatch with message: ${e.getMessage}")
      Right(UnlockingFailed)
  }
}
  def fireIncorpUpdateBatch: Future[Seq[Boolean]] = {
    queueRepository.getIncorpUpdates(fetchSize) flatMap { updates =>
      Future.sequence( updates map { update => for {
        checkTSresult <- checkTimestamp(update.timestamp)
        fireResult <- fire(checkTSresult, update)
      } yield fireResult
      })
    }
  }

  def fire(tsRes: Boolean, update: QueuedIncorpUpdate): Future[Boolean] = {
    if (tsRes){
      fireIncorpUpdate(update)
    } else {
      Logger.info(s"[SubscriptionFiringService][fire] QueuedIncorpUpdate with transactionId: ${update.incorpUpdate.transactionId} and timestamp: ${update.timestamp}" +
        s" cannot be processed at this time as the timestamp is in the future")
      Future.successful(false)
    }
  }

  def checkTimestamp(ts: DateTime): Future[Boolean] = {
    Future(ts.getMillis <= DateTime.now.getMillis)
  }

  private def deleteSub(transId: String, regime: String, subscriber: String): Future[Boolean] = {
    subscriptionsRepository.deleteSub(transId, regime, subscriber) map {
      case DefaultWriteResult(true, 1, _, _, _, _) =>
        Logger.info(s"[SubscriptionFiringService][deleteSub] Subscription with transactionId: ${transId} deleted sub for $regime")
        true
      case _ => Logger.info(s"[SubscriptionFiringService][deleteSub] Subscription with transactionId: ${transId} failed to delete")
        false
    }
  }

  private def deleteQueuedIU(transId: String): Future[Boolean] = {
    subscriptionsRepository.getSubscriptions(transId) flatMap{
      case h :: t => {
        Logger.info(s"[SubscriptionFiringService][deleteQueuedIU] QueuedIncorpUdate with transactionId: ${transId} cannot be deleted as there are other " +
          s"subscriptions with this transactionId")
        Future.successful(false)
      }
      case Nil => {
        queueRepository.removeQueuedIncorpUpdate(transId).map{
          case true => Logger.info(s"[SubscriptionFiringService][deleteQueuedIU] QueuedIncorpUpdate with transactionId: ${transId} was deleted")
            true
          case false => Logger.info(s"[SubscriptionFiringService][deleteQueuedIU] QueuedIncorpUpdate with transactionId: ${transId} failed to delete")
            false
        }
      }
    }
  }

  private[services] val httpHttpsConverter = (url: String) => if(useHttpsFireSubs) url.replace("http://", "https://").replace(":80", ":443") else url

  private[services] def fireIncorpUpdate(iu: QueuedIncorpUpdate): Future[Boolean] = {
    subscriptionsRepository.getSubscriptions(iu.incorpUpdate.transactionId) flatMap { subscriptions =>
      Future.sequence( subscriptions map { sub =>
        val iuResponse: IncorpUpdateResponse = IncorpUpdateResponse(sub.regime, sub.subscriber, sub.callbackUrl, iu.incorpUpdate)

        firingSubsConnector.connectToAnyURL(iuResponse, httpHttpsConverter(sub.callbackUrl))(hc) flatMap { response =>
          Logger.info(s"[SubscriptionFiringService] [fireIncorpUpdate] - Posting response to callback for txid : ${iu.incorpUpdate.transactionId} was successful")
          response.status match {
            case 202 => {
              val newTS = DateTime.now.plusSeconds(queueRetryDelay)
              queueRepository.updateTimestamp(sub.transactionId, newTS).map(_ => false)
            }
            case _   =>  deleteSub(sub.transactionId, sub.regime, sub.subscriber)
          }
        } recoverWith {
          case e : Exception =>
            Logger.info(s"[SubscriptionFiringService][fireIncorpUpdate] Subscription with transactionId: ${sub.transactionId} failed to return a 200 response")
            val newTS = DateTime.now.plusSeconds(queueFailureDelay)
            queueRepository.updateTimestamp(sub.transactionId, newTS).map(_ => false)
        }
      } ) flatMap { sb =>
        deleteQueuedIU(iu.incorpUpdate.transactionId)
      }
    }
  }
}
