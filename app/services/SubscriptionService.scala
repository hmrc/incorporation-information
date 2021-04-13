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
import models.{IncorpUpdate, Subscription}
import play.api.Logger
import reactivemongo.api.commands.DefaultWriteResult
import repositories._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}


class SubscriptionServiceImpl @Inject()(injSubRepo: SubscriptionsMongo,
                                        injIncorpRepo: IncorpUpdateMongo,
                                        config: MicroserviceConfig,
                                        incorpUpdateServiceInj: IncorpUpdateService
                                       )(implicit val ec: ExecutionContext) extends SubscriptionService {
  lazy val incorpUpdateService: IncorpUpdateService = incorpUpdateServiceInj
  override lazy val subRepo: SubscriptionsMongoRepository = injSubRepo.repo
  override lazy val incorpRepo: IncorpUpdateMongoRepository = injIncorpRepo.repo
  override lazy val forcedSubDelay: Int = config.forcedSubscriptionDelay
}

trait SubscriptionService {

  implicit val ec: ExecutionContext
  protected val subRepo: SubscriptionsRepository
  protected val incorpRepo: IncorpUpdateRepository
  protected val incorpUpdateService: IncorpUpdateService
  protected val forcedSubDelay: Int

  def checkForSubscription(transactionId: String,
                           regime: String,
                           subscriber: String,
                           callBackUrl: String,
                           forced: Boolean): Future[SubscriptionStatus] =
    checkForIncorpUpdate(transactionId) flatMap {
      case Some(incorpUpdate) if !forced => Future.successful(IncorpExists(incorpUpdate))
      case Some(incorpUpdate) => forceSubscription(transactionId, regime, subscriber, callBackUrl, incorpUpdate)
      case None => addSubscription(transactionId, regime, subscriber, callBackUrl)
    }

  private[services] def forceSubscription(transactionId: String,
                                          regime: String,
                                          subscriber: String,
                                          callBackUrl: String,
                                          incorpUpdate: IncorpUpdate
                                         ): Future[SubscriptionStatus] = {
    val queuedItem = incorpUpdateService.createQueuedIncorpUpdate(incorpUpdate, Some(forcedSubDelay))
    addSubscription(transactionId, regime, subscriber, callBackUrl) flatMap {
      case SuccessfulSub(_) =>
        Logger.info(s"[SubscriptionService] [forceSubscription] subscription for transaction id : $transactionId forced successfully for regime : $regime")
        incorpUpdateService.upsertToQueue(queuedItem) map {
          case true => SuccessfulSub(forced = true)
          case _ => FailedSub
        }
      case _ => Future.successful(FailedSub)
    }
  }

  def addSubscription(transactionId: String,
                      regime: String,
                      subscriber: String,
                      callbackUrl: String
                     ): Future[SubscriptionStatus] = {
    val sub = Subscription(transactionId, regime, subscriber, callbackUrl)
    subRepo.insertSub(sub) map {
      case UpsertResult(a, b, Seq()) =>
        Logger.info(s"[MongoSubscriptionsRepository] [insertSub] $a was updated and $b was upserted for transactionId: $transactionId")
        SuccessfulSub()
      case UpsertResult(_, _, errs) if errs.nonEmpty =>
        Logger.error(s"[SubscriptionService] [addSubscription] Error encountered when attempting to add a subscription - ${errs.toString()}")
        FailedSub
    }
  }

  private[services] def checkForIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]] = {
    incorpRepo.getIncorpUpdate(transactionId)
  }

  def deleteSubscription(transactionId: String,
                         regime: String,
                         subscriber: String
                        ): Future[UnsubscribeStatus] =
    subRepo.deleteSub(transactionId, regime, subscriber) map {
      case DefaultWriteResult(true, 1, _, _, _, _) => Logger.info(s"[SubscriptionService] [deleteSubscription] Subscription with transactionId: $transactionId, " +
        s"and regime: $regime, and subscriber: $subscriber was deleted")
        DeletedSub
      case e@_ =>
        Logger.warn(s"[SubscriptionsRepository] [deleteSub] Didn't delete the subscription with TransId: $transactionId, and regime: $regime, and subscriber: $subscriber." +
          s"Error message: $e")
        NotDeletedSub
    }

  def getSubscription(transactionId: String, regime: String, subscriber: String): Future[Option[Subscription]] =
    subRepo.getSubscription(transactionId, regime, subscriber)

}