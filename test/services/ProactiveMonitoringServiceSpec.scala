/*
 * Copyright 2021 HM Revenue & Customs
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

import Helpers.SCRSSpec
import connectors.{FailedTransactionalAPIResponse, IncorporationAPIConnector, PublicCohoApiConnectorImpl, SuccessfulTransactionalAPIResponse}
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.lock.LockKeeper
import utils.Base64
import play.api.test.Helpers._

import scala.concurrent.Future


class ProactiveMonitoringServiceSpec extends SCRSSpec {

  val mockTransactionalConnector: IncorporationAPIConnector = mock[IncorporationAPIConnector]
  val mockPublicCohoConnector: PublicCohoApiConnectorImpl = mock[PublicCohoApiConnectorImpl]
  val mockLockKeeper: LockKeeper = mock[LockKeeper]

  val encodedTransactionId: String = Base64.encode("test-txid")
  val transactionId: String = "test-txid"

  val encodedCrn: String = Base64.encode("test-crn")
  val crn: String = "test-crn"

  class Setup {
    val service: ProactiveMonitoringService = new ProactiveMonitoringService {
      val transactionalConnector: IncorporationAPIConnector = mockTransactionalConnector
      override val lockKeeper: LockKeeper = mockLockKeeper
      val publicCohoConnector: PublicCohoApiConnectorImpl = mockPublicCohoConnector
      val transactionIdToPoll: String = encodedTransactionId
      val crnToPoll: String = encodedCrn
    }
  }

  implicit val hc : HeaderCarrier = HeaderCarrier()

  val testJson: JsValue = Json.parse("""{"test":"json"}""")

  "pollTransactionalAPI" should {

    "return a success response" in new Setup {
      when(mockTransactionalConnector.fetchTransactionalData(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(testJson)))

      val result: String = await(service.pollTransactionalAPI)
      result shouldBe "success"
    }

    "return a failed response" in new Setup {
      when(mockTransactionalConnector.fetchTransactionalData(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))

      val result: String = await(service.pollTransactionalAPI)
      result shouldBe "failed"
    }
  }

  "pollPublicAPI" should {

    "return a success response" in new Setup {
      when(mockPublicCohoConnector.getCompanyProfile(eqTo(crn), any())(any()))
        .thenReturn(Future.successful(Some(testJson)))

      val result: String = await(service.pollPublicAPI)
      result shouldBe "success"
    }

    "return a failed response" in new Setup {
      when(mockPublicCohoConnector.getCompanyProfile(eqTo(crn), any())(any()))
        .thenReturn(Future.successful(None))

      val result: String = await(service.pollPublicAPI)
      result shouldBe "failed"
    }
  }

  "pollAPIs" should {

    "return a success response from each API" in new Setup {
      when(mockTransactionalConnector.fetchTransactionalData(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(testJson)))
      when(mockPublicCohoConnector.getCompanyProfile(eqTo(crn), any())(any()))
        .thenReturn(Future.successful(Some(testJson)))

      val result: (String, String) = await(service.pollAPIs)
      result shouldBe ("polling transactional API - success", "polling public API - success")
    }

    "return a failed response from each API" in new Setup {
      when(mockTransactionalConnector.fetchTransactionalData(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(FailedTransactionalAPIResponse))
      when(mockPublicCohoConnector.getCompanyProfile(eqTo(crn), any())(any()))
        .thenReturn(Future.successful(None))

      val result: (String, String) = await(service.pollAPIs)
      result shouldBe ("polling transactional API - failed", "polling public API - failed")
    }

    "return a success response from the transactional API and a failed response from the public API" in new Setup {
      when(mockTransactionalConnector.fetchTransactionalData(eqTo(transactionId))(any()))
        .thenReturn(Future.successful(SuccessfulTransactionalAPIResponse(testJson)))
      when(mockPublicCohoConnector.getCompanyProfile(eqTo(crn), any())(any()))
        .thenReturn(Future.successful(None))

      val result: (String, String) = await(service.pollAPIs)
      result shouldBe ("polling transactional API - success", "polling public API - failed")
    }
  }
}
