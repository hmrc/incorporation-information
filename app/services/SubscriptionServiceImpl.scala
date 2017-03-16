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

import com.google.inject.ImplementedBy
import model.Subscription
import mongo.MongoSubscriptionsRepository
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

/**
  * Created by jackie on 16/03/17.
  */
class SubscriptionServiceImpl @Inject()(val connector: MongoSubscriptionsRepository) extends SubscriptionService

@ImplementedBy(classOf[SubscriptionServiceImpl])
trait SubscriptionService {

  protected val connector: MongoSubscriptionsRepository

  def addSubscription(subscriber: String, discriminator: String, transactionId: String)(implicit hc: HeaderCarrier): Future[WriteResult] = {
    val sub = Subscription(subscriber, discriminator, transactionId)
    connector.insertSub(sub)
  }


}

