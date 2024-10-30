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

package test.helpers

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.{DefaultApplicationLifecycle, bind}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

trait SCRSMongoSpec extends PlaySpec with MongoSupport with BeforeAndAfterEach with BeforeAndAfterAll
  with ScalaFutures with Eventually with GuiceOneAppPerSuite with FakeAppConfig {

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig())
    .overrides(bind(classOf[MongoComponent]).toInstance(mongoComponent))
    .build()

  lazy val applicationLifeCycle = new DefaultApplicationLifecycle

  implicit def formatToOFormat[T](implicit format: Format[T]): OFormat[T] = new OFormat[T] {
    override def writes(o: T): JsObject = format.writes(o).as[JsObject]

    override def reads(json: JsValue): JsResult[T] = format.reads(json)
  }
}