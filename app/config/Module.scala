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

package config

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import connectors._
import controllers.test.{FeatureSwitchController, FeatureSwitchControllerImpl}
import controllers.{SubscriptionController, SubscriptionControllerImpl, TransactionalController, TransactionalControllerImpl}
import jobs.{FireSubscriptionsJobImpl, IncorpUpdatesJobImpl}
import repositories._
import services._
import uk.gov.hmrc.play.config.inject.{DefaultServicesConfig, ServicesConfig}
import uk.gov.hmrc.play.scheduling.ScheduledJob

class Module extends AbstractModule {

  override def configure(): Unit = {

    bind(classOf[ServicesConfig]).to(classOf[DefaultServicesConfig]).asEagerSingleton()
    bind(classOf[MicroserviceConfig]).to(classOf[MicroserviceConfigImpl]).asEagerSingleton()

    // controllers
    bind(classOf[SubscriptionController]).to(classOf[SubscriptionControllerImpl])
    bind(classOf[TransactionalController]).to(classOf[TransactionalControllerImpl])
    bind(classOf[FeatureSwitchController]).to(classOf[FeatureSwitchControllerImpl])

    // connectors
    bind(classOf[IncorporationCheckAPIConnector]).to(classOf[IncorporationCheckAPIConnectorImpl])
    bind(classOf[TransactionalConnector]).to(classOf[TransactionalConnectorImpl])
    bind(classOf[FiringSubscriptionsConnector]).to(classOf[FiringSubscriptionsConnectorImpl])

    // services
    bind(classOf[IncorpUpdateService]).to(classOf[IncorpUpdateServiceImpl])
    bind(classOf[TransactionalService]).to(classOf[TransactionalServiceImpl])
    bind(classOf[SubscriptionService]).to(classOf[SubscriptionServiceImpl])
    bind(classOf[SubscriptionFiringService]).to(classOf[SubscriptionFiringServiceImpl])

    // jobs
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("incorp-update-job")).to(classOf[IncorpUpdatesJobImpl])
    bind(classOf[ScheduledJob]).annotatedWith(Names.named("fire-subs-job")).to(classOf[FireSubscriptionsJobImpl])
  }
}
