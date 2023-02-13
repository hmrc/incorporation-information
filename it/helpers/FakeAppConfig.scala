/*
 * Copyright 2023 HM Revenue & Customs
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

package helpers

import utils.Base64

trait FakeAppConfig {

  import WiremockHelper._

  private val txId = "test-txid"
  private val crn = "test-crn"

  def fakeConfig(additionalConfig: Map[String, Any] = Map.empty): Map[String, Any] = Map(
    "metrics.enabled" -> true,
    "auditing.enabled" -> false,
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "Test.auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "Test.auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "microservice.services.incorp-update-api.stub-url" -> s"http://${wiremockHost}:${wiremockPort}/incorporation-frontend-stubs",
    "microservice.services.incorp-update-api.url" -> s"$wiremockUrl",
    "microservice.services.incorp-update-api.token" -> "N/A",
    "microservice.services.incorp-update-api.itemsToFetch" -> "3",
    "microservice.services.incorp-frontend-stubs.host" -> wiremockHost,
    "microservice.services.incorp-frontend-stubs.port" -> s"$wiremockPort",
    "microservice.services.public-coho-api.baseUrl" -> s"$wiremockUrl",
    "microservice.services.transaction-id-to-poll" -> Base64.encode(txId),
    "microservice.services.crn-to-poll" -> Base64.encode(crn),
    "microservice.services.rai-alert-logging-time" -> "00:00:00_23:59:59"
  ) ++ additionalConfig
}
