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
package helpers

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.libs.ws.WSClient
import uk.gov.hmrc.play.test.UnitSpec
import utils.{FeatureSwitch, SCRSFeatureSwitches}

trait IntegrationSpecBase extends UnitSpec
  with GivenWhenThen
  with OneServerPerSuite with ScalaFutures with IntegrationPatience with Matchers
  with WiremockHelper with BeforeAndAfterEach with BeforeAndAfterAll with FakeAppConfig {

  def ws(implicit app: Application): WSClient = app.injector.instanceOf[WSClient]

  def setupFeatures(submissionCheck: Boolean = false,
                    transactionalAPI: Boolean = false,
                    fireSubscriptions: Boolean = false,
                    scheduledMetrics: Boolean = false): Unit = {
    def enableFeature(fs: FeatureSwitch, enabled: Boolean) = {
      enabled match {
        case true => FeatureSwitch.enable(fs)
        case _ => FeatureSwitch.disable(fs)
      }
    }

    enableFeature(SCRSFeatureSwitches.scheduler, submissionCheck)
    enableFeature(SCRSFeatureSwitches.transactionalAPI, transactionalAPI)
    enableFeature(SCRSFeatureSwitches.fireSubs, fireSubscriptions)
    enableFeature(SCRSFeatureSwitches.scheduledMetrics, scheduledMetrics)
  }

  def stubAuditEvents: StubMapping = stubPost("/write/audit/merged", 200, "")

  override def beforeEach() = {
    resetWiremock()
  }

  override def beforeAll() = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll() = {
    stopWiremock()
    super.afterAll()
  }
}