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

import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.AuthConnector
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.ws._
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.play.microservice.config.LoadAuditingConfig

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override lazy val auditConnector: AuditConnector = MicroserviceAuditConnector
}

object WSHttp extends WSHttp

trait WSHttp extends
  WSGet with HttpGet with
  WSPut with HttpPut with
  WSPost with HttpPost with
  WSDelete with HttpDelete with
  WSPatch with HttpPatch with AppName with Hooks

object MicroserviceAuditConnector extends AuditConnector with RunMode {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

object MicroserviceAuthConnector extends AuthConnector with ServicesConfig with WSHttp {
  override val authBaseUrl = baseUrl("auth")
}

object WSHttpProxy extends WSHttp with WSProxy with RunMode with HttpAuditing with ServicesConfig {
  override lazy val appName = getString("appName")
  override lazy val wsProxyServer = WSProxyConfiguration(s"proxy")
  override lazy val auditConnector = MicroserviceAuditConnector
}
