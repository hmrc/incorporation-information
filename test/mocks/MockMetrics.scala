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

package mocks

import com.codahale.metrics.{Counter, Timer}
import org.scalatestplus.mockito.MockitoSugar
import repositories.SubscriptionsMongoRepository
import services.MetricsService
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.play.bootstrap.metrics.Metrics


class MockMetrics extends MetricsService with MockitoSugar {
  override val lockKeeper: LockService = mock[LockService]
  val mockCounter = new Counter

  val mockMetrics = mock[Metrics]

  val mockTimer = mock[Timer]

  val metrics: Metrics = mockMetrics
  override val publicCohoApiFailureCounter: Counter = mockCounter
  override val publicCohoApiSuccessCounter: Counter = mockCounter
  override val transactionApiFailureCounter: Counter = mockCounter
  override val transactionApiSuccessCounter: Counter = mockCounter
  override val subRepo = mock[SubscriptionsMongoRepository]
  override val publicAPITimer: Timer = mockTimer
  override val internalAPITimer: Timer = mockTimer
}
