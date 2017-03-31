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

import javax.inject.Inject

import models.{IncorpUpdate, Subscription}
import play.api.Logger
import repositories._
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SubscriptionServiceImpl @Inject()(val subRepo: SubscriptionsMongo, val incorpRepo: IncorpUpdateMongo) extends SubscriptionService

trait SubscriptionService {

  protected val subRepo: SubscriptionsMongo
  protected val incorpRepo: IncorpUpdateMongo


  def checkForSubscription(transactionId: String, regime: String, subscriber: String, callBackUrl: String)(implicit hc: HeaderCarrier): Future[SubscriptionStatus] = {
     checkForIncorpUpdate(transactionId) flatMap {
      case Some(incorpUpdate) => Future.successful(IncorpExists(incorpUpdate))
      case None => addSubscription(transactionId, regime, subscriber, callBackUrl)
    }
  }

  def addSubscription(transactionId: String, regime: String, subscriber: String, callbackUrl: String)(implicit hc: HeaderCarrier): Future[SubscriptionStatus] = {
    val sub = Subscription(transactionId, regime, subscriber, callbackUrl)
    subRepo.repo.insertSub(sub) map {
      case UpsertResult(a, b, Seq()) =>
        Logger.info(s"[MongoSubscriptionsRepository] [insertSub] $a was updated and $b was upserted for transactionId: $transactionId")
        SuccessfulSub
      case UpsertResult(_, _, errs) if errs.nonEmpty =>
        Logger.error(s"[SubscriptionService] [addSubscription] Error encountered when attempting to add a subscription - ${errs.toString()}")
        FailedSub
    }
  }

  private[services] def checkForIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]] = {
    incorpRepo.repo.getIncorpUpdate(transactionId)
  }

  def deleteSubscription(transactionId: String, regime: String, subscriber: String): Future[SubscriptionStatus] = {
    subRepo.repo.deleteSub(transactionId, regime, subscriber) map {
      case DeletedSub => Logger.info(s"[SubscriptionService] [deleteSubscription] Subscription with transactionId: $transactionId, " +
        s"and regime: $regime, and subscriber: $subscriber was deleted")
        DeletedSub
      case FailedSub => FailedSub
    }
  }

  def getSubscription(transactionId: String, regime: String, subscriber: String): Future[Option[Subscription]] = {
    subRepo.getSubscription(transactionId, regime, subscriber)
  }


}

