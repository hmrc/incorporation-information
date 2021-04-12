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

package mocks

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.Metrics
import org.scalatest.mock.MockitoSugar
import repositories.SubscriptionsMongoRepository
import services.MetricsService
import uk.gov.hmrc.lock.LockKeeper


class MockMetrics extends MetricsService with MockitoSugar{
  override val lockKeeper: LockKeeper = mock[LockKeeper]
  val mockCounter = new Counter

  val mockMetrics = mock[Metrics]

  val mockTimer = mock[Timer]

  val metrics = mockMetrics
  override val publicCohoApiFailureCounter: Counter = mockCounter
  override val publicCohoApiSuccessCounter: Counter = mockCounter
  override val transactionApiFailureCounter: Counter = mockCounter
  override val transactionApiSuccessCounter: Counter = mockCounter
  override val subRepo = mock[SubscriptionsMongoRepository]
  override val publicAPITimer = mockTimer
  override val internalAPITimer = mockTimer
}
