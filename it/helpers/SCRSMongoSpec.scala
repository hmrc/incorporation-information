/*
* Copyright 2016 HM Revenue & Customs
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
import play.modules.reactivemongo.ReactiveMongoComponentImpl
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{WithFakeApplication, UnitSpec}
import play.api.inject.{DefaultApplicationLifecycle, ApplicationLifecycle}

class SCRSMongoSpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach with ScalaFutures with Eventually with WithFakeApplication {

  lazy val applicationLifeCycle = new DefaultApplicationLifecycle
  val reactiveMongoComponent = new ReactiveMongoComponentImpl(fakeApplication, applicationLifeCycle)
}
