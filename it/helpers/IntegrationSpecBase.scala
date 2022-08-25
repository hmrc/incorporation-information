/*
 * Copyright 2022 HM Revenue & Customs
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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger => LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{Application, LoggerLike}
import play.api.libs.ws.WSClient
import utils.{FeatureSwitch, SCRSFeatureSwitches}

import scala.collection.JavaConverters._

trait IntegrationSpecBase extends PlaySpec
  with GivenWhenThen
  with GuiceOneServerPerSuite with ScalaFutures with IntegrationPatience
  with WiremockHelper with BeforeAndAfterEach with BeforeAndAfterAll with FakeAppConfig {

  implicit def ws(implicit app: Application): WSClient = app.injector.instanceOf[WSClient]

  def setupFeatures(submissionCheck: Boolean = false,
                    transactionalAPI: Boolean = false,
                    fireSubscriptions: Boolean = false,
                    scheduledMetrics: Boolean = false): Unit = {
    def enableFeature(fs: FeatureSwitch, enabled: Boolean) = {
      if (enabled) {
        FeatureSwitch.enable(fs)
      } else {
        FeatureSwitch.disable(fs)
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

  def withCaptureOfLoggingFrom(logger: LogbackLogger)(body: (=> List[ILoggingEvent]) => Unit) {
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(logger.getLoggerContext)
    appender.start()
    logger.addAppender(appender)
    logger.setLevel(Level.ALL)
    logger.setAdditive(true)
    body(appender.list.asScala.toList)
  }

  def withCaptureOfLoggingFrom(logger: LoggerLike)(body: (=> List[ILoggingEvent]) => Unit) {
    withCaptureOfLoggingFrom(logger.logger.asInstanceOf[LogbackLogger])(body)
  }
}