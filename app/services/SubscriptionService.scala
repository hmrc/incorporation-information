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

import models.Subscription
import repositories._
import models.IncorpUpdate
import play.api.Logger
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object SubscriptionService
extends SubscriptionService {

  override protected val subRepo = Repositories.smRepository
  override protected val incorpRepo = Repositories.incorpUpdateRepository

}

trait SubscriptionService {

  protected val subRepo: SubscriptionsRepository
  protected val incorpRepo: IncorpUpdateRepository


  def checkForSubscription(transactionId: String, regime: String, subscriber: String, callBackUrl: String)(implicit hc: HeaderCarrier): Future[SubscriptionStatus] = {
    checkForIncorpUpdate(transactionId) flatMap {
      case Some(incorpUpdate) => {
        Future.successful(IncorpExists(incorpUpdate))}
      case None => {
        addSubscription(transactionId, regime, subscriber, callBackUrl)
     checkForIncorpUpdate(transactionId) flatMap {
      case Some(incorpUpdate) => {
         Future.successful(IncorpExists(incorpUpdate))}
      case None => {
         addSubscription(transactionId, regime, subscriber, callBackUrl)
      }
    }
  }

  def addSubscription(transactionId: String, regime: String, subscriber: String, callbackUrl: String)(implicit hc: HeaderCarrier): Future[SubscriptionStatus] = {
    val sub = Subscription(transactionId, regime, subscriber, callbackUrl)
    subRepo.insertSub(sub) map {
      case UpsertResult(_, _, Seq()) => SuccessfulSub
      case UpsertResult(_, _, errs) if errs.nonEmpty =>
        Logger.error(s"[SubscriptionService] [addSubscription] Encountered when attempting to add a subscription - ${errs.toString()}")
        FailedSub
    }
    subRepo.insertSub(sub)
  }

  private[services] def checkForIncorpUpdate(transactionId: String): Future[Option[IncorpUpdate]] = {
    incorpRepo.getIncorpUpdate(transactionId)
  }
}

