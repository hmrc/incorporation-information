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

package services

import connectors.IncorporationCheckAPIConnector
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import repositories.{IncorpUpdateRepository, TimepointRepository}
import uk.gov.hmrc.play.test.UnitSpec

/**
  * Created by jackie on 22/03/17.
  */
class IncorpUpdateServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockIncorporationCheckAPIConnector = mock[IncorporationCheckAPIConnector]
  val mockIncorpUpdateRepository = mock[IncorpUpdateRepository]
  val mockTimepointRepository = mock[TimepointRepository]

  override def beforeEach() {
    resetMocks()
  }

  def resetMocks() = {
    reset(mockIncorporationCheckAPIConnector)
    reset(mockIncorpUpdateRepository)
    reset(mockTimepointRepository)
  }

  trait mockService extends IncorpUpdateService {
    val incorporationCheckAPIConnector = mockIncorporationCheckAPIConnector
    val incorpUpdateRepository = mockIncorpUpdateRepository
    val timepointRepository = mockTimepointRepository
  }

  trait Setup {
    val service = new mockService {}
  }

  trait SetupMockedAudit {
    val service = new mockService {
      //override def processSuccessDesResponse(item: IncorpUpdate, ctReg: CorporationTaxRegistration, auditDetail: JsObject)(implicit hc: HeaderCarrier) = Future.successful(true)
    }
  }


  "updateNextIncorpUpdateJobLot" should {
    "return a string stating that states 'No Incorporation updates were fetched'" in new Setup {
      //when()
    }
  }
}


