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

import javax.inject.Inject

import uk.gov.hmrc.play.config.inject.ServicesConfig

class MicroserviceConfigImpl @Inject()(val config: ServicesConfig) extends MicroserviceConfig

trait MicroserviceConfig {
  protected val config: ServicesConfig

  lazy val incorpFrontendStubUrl = config.getConfString("incorp-update-api.stub-url", throw new Exception("incorp-update-api.stub-url not found"))
  lazy val companiesHouseUrl = config.getConfString("incorp-update-api.url", throw new Exception("incorp-update-api.url not found"))

  lazy val incorpUpdateCohoApiAuthToken = config.getConfString("incorp-update-api.token", throw new Exception("incorp-update-api.token not found"))

  lazy val incorpUpdateItemsToFetch = config.getConfString("incorp-update-api.itemsToFetch", throw new Exception("incorp-update-api.itemsToFetch not found"))

  lazy val queueFailureDelay = config.getConfInt("fire-subs-job.queueFailureDelaySeconds", throw new Exception("fire-subs-api.queueFailureDelaySeconds not found"))

  lazy val cohoPublicBaseUrl = config.getConfString("public-coho-api.baseUrl", throw new Exception("public-coho-api.baseUrl not found"))

  lazy val cohoPublicApiAuthToken = config.getConfString("public-coho-api.authToken", throw new Exception("public-coho-api.authToken not found"))

  lazy val cohoStubbedUrl = config.getConfString("public-coho-api.stub-url", throw new Exception("public-coho-api.stub-url not found"))



}

