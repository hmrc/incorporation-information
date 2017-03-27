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
import repositories.{Repositories, SubscriptionStatus, SubscriptionsRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

// TODO - DI
object SubscriptionService extends SubscriptionService {
  override protected val repo = Repositories.msRepository
}

trait SubscriptionService {

  protected val repo: SubscriptionsRepository

  def addSubscription(transactionId: String, regime: String, subscriber: String)(implicit hc: HeaderCarrier): Future[SubscriptionStatus] = {
    val sub = Subscription(transactionId, regime, subscriber)
    repo.insertSub(sub)
  }


}

