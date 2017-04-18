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

import javax.inject.{Inject, Singleton}

import connectors.FiringSubscriptionsConnector
import models.{IncorpUpdateResponse, QueuedIncorpUpdate}
import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.api.commands.DefaultWriteResult
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class SubscriptionFiringServiceImpl @Inject()(
                                               fsConnector: FiringSubscriptionsConnector,
                                               injQueueRepo: QueueMongo,
                                               injSubRepo: SubscriptionsMongo
                                             ) extends SubscriptionFiringService {
  override val firingSubsConnector = fsConnector
  override val queueRepository = injQueueRepo.repo
  override val subscriptionsRepository = injSubRepo.repo

  implicit val hc = HeaderCarrier()
}

trait SubscriptionFiringService {
  val firingSubsConnector: FiringSubscriptionsConnector
  val queueRepository: QueueRepository
  val subscriptionsRepository: SubscriptionsRepository

  implicit val hc: HeaderCarrier


  def fireIncorpUpdateBatch: Future[Seq[Boolean]] = {
    queueRepository.getIncorpUpdates flatMap { updates =>
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
    subscriptionsRepository.deleteSub(transId, regime, subscriber).map(res => res match {
      case DefaultWriteResult(true, _, _, _, _, _) =>
        Logger.info(s"[SubscriptionFiringService][deleteSub] Subscription with transactionId: ${transId} deleted sub for $regime")
        true
      case _ => Logger.info(s"[SubscriptionFiringService][deleteSub] Subscription with transactionId: ${transId} failed to delete")
        false
    })
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


  private def fireIncorpUpdate(iu: QueuedIncorpUpdate): Future[Boolean] = {

    subscriptionsRepository.getSubscriptions(iu.incorpUpdate.transactionId) flatMap { subscriptions =>
      Future.sequence( subscriptions map { sub =>
        val iuResponse: IncorpUpdateResponse = IncorpUpdateResponse(sub.regime, sub.subscriber, sub.callbackUrl, iu.incorpUpdate)

        firingSubsConnector.connectToAnyURL(iuResponse, sub.callbackUrl)(hc) flatMap { response =>
            deleteSub(sub.transactionId, sub.regime, sub.subscriber)
        } recoverWith {
          case e : Exception =>
            Logger.info(s"[SubscriptionFiringService][fireIncorpUpdate] Subscription with transactionId: ${sub.transactionId} failed to return a 200 response")
            queueRepository.updateTimestamp(sub.transactionId)
            Future(false)
        }
      } ) flatMap { sb =>
        deleteQueuedIU(iu.incorpUpdate.transactionId)
      }
    }
  }



}
