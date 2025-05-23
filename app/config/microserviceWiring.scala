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

package config

import org.apache.pekko.actor.ActorSystem
import com.typesafe.config.Config
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSProxyServer}
import uk.gov.hmrc.http.hooks.HttpHook
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.AppName
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.play.http.ws._

import javax.inject.Inject


class WSHttpProxyImpl @Inject()(val microserviceConfig: MicroserviceConfig,
                                val config: Configuration,
                                val actorSystem: ActorSystem,
                                override val wsClient: WSClient,
                                val auditConnector: AuditConnector) extends WSHttpProxy

trait WSHttpProxy extends HttpClient with WSHttp with HttpAuditing with WSProxy {
  val config: Configuration

  lazy val wsProxyServer: Option[WSProxyServer] = WSProxyConfiguration.buildWsProxyServer(config)

  override lazy val configuration: Config = config.underlying

  override val appName: String = AppName.fromConfiguration(config)

  override val hooks: Seq[HttpHook] = Seq(AuditingHook)
}

