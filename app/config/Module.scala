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

package config

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors._
import controllers.test._
import controllers.{SubscriptionController, SubscriptionControllerImpl, TransactionalController, TransactionalControllerImpl}
import jobs._
import repositories._
import services._
import utils.{DateCalculators, DateCalculatorsImpl}

class Module extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[LockRepositoryProvider]).to(classOf[LockRepositoryProviderImpl]).asEagerSingleton()

    bind(classOf[MicroserviceConfig]).to(classOf[MicroserviceConfigImpl]).asEagerSingleton()
    bind(classOf[WSHttpProxy]).to(classOf[WSHttpProxyImpl]).asEagerSingleton()

    bind(classOf[QueueMongo]).to(classOf[QueueMongoImpl]).asEagerSingleton()
    bind(classOf[IncorpUpdateMongo]).to(classOf[IncorpUpdateMongoImpl]).asEagerSingleton()
    bind(classOf[TimepointMongo]).asEagerSingleton()
    bind(classOf[SubscriptionsMongo]).asEagerSingleton()

    bind(classOf[IncorpUpdateService]).to(classOf[IncorpUpdateServiceImpl]).asEagerSingleton()
    bind(classOf[TransactionalService]).to(classOf[TransactionalServiceImpl]).asEagerSingleton()
    bind(classOf[SubscriptionService]).to(classOf[SubscriptionServiceImpl]).asEagerSingleton()
    bind(classOf[SubscriptionFiringService]).to(classOf[SubscriptionFiringServiceImpl]).asEagerSingleton()
    bind(classOf[ProactiveMonitoringService]).to(classOf[ProactiveMonitoringServiceImpl]).asEagerSingleton()

    bind(classOf[ScheduledJob]).annotatedWith(Names.named("incorp-update-job")).to(classOf[IncorpUpdatesJob]).asEagerSingleton()
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("fire-subs-job")).to(classOf[FireSubscriptionsJob]).asEagerSingleton()
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("metrics-job")).to(classOf[MetricsJob]).asEagerSingleton()
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("proactive-monitoring-job")).to(classOf[ProactiveMonitoringJob]).asEagerSingleton()

    bind(classOf[ManualTriggerController]).to(classOf[ManualTriggerControllerImpl]).asEagerSingleton()
    bind(classOf[SubscriptionController]).to(classOf[SubscriptionControllerImpl]).asEagerSingleton()
    bind(classOf[TransactionalController]).to(classOf[TransactionalControllerImpl]).asEagerSingleton()
    bind(classOf[FeatureSwitchController]).to(classOf[FeatureSwitchControllerImpl]).asEagerSingleton()

    bind(classOf[CallbackTestEndpointController]).to(classOf[CallbackTestEndpointControllerImpl]).asEagerSingleton()
    bind(classOf[IncorpUpdateController]).to(classOf[IncorpUpdateControllerImpl]).asEagerSingleton()
    bind(classOf[MetricsService]).to(classOf[MetricsServiceImpl]).asEagerSingleton()
    bind(classOf[IncorporationAPIConnector]).to(classOf[IncorporationAPIConnectorImpl]).asEagerSingleton()
    bind(classOf[FiringSubscriptionsConnector]).to(classOf[FiringSubscriptionsConnectorImpl]).asEagerSingleton()
    bind(classOf[PublicCohoApiConnector]).to(classOf[PublicCohoApiConnectorImpl]).asEagerSingleton()

    bind(classOf[DateCalculators]).to(classOf[DateCalculatorsImpl]).asEagerSingleton()
    bind(classOf[StartUpJobs]).to(classOf[StartUpJobsImpl]).asEagerSingleton()
  }
}