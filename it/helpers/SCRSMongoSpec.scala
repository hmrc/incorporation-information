/*
* Copyright 2020 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.inject.DefaultApplicationLifecycle
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.{Application, Environment}
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

trait SCRSMongoSpec extends UnitSpec with  MongoSpecSupport with BeforeAndAfterEach
  with ScalaFutures with Eventually with WithFakeApplication with FakeAppConfig {

  override lazy val fakeApplication: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig())
    .bindings(bindModules:_*)
    .build()

  lazy val applicationLifeCycle = new DefaultApplicationLifecycle
  lazy val reactiveMongoComponent = new ReactiveMongoComponentImpl(fakeApplication.configuration,fakeApplication.injector.instanceOf[Environment],applicationLifeCycle)

  implicit def formatToOFormat[T](implicit format: Format[T]): OFormat[T] = new OFormat[T] {
    override def writes(o: T): JsObject = format.writes(o).as[JsObject]
    override def reads(json: JsValue): JsResult[T] = format.reads(json)
  }
}