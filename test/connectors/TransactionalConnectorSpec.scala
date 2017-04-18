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

package connectors

import Helpers.SCRSSpec
import org.mockito.Matchers.{any, eq => eqTo}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http.{HttpException, HttpGet, NotFoundException}
import uk.gov.hmrc.play.http.ws.WSProxy
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}
import org.mockito.Mockito._
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class TransactionalConnectorSpec extends SCRSSpec {

  val mockHttp = mock[HttpGet with WSProxy]
  val mockFeatureSwitch = mock[SCRSFeatureSwitches]

  val cohoUrlValue = "http://test.CohoUrl"
  val stubUrlValue = "http://test.StubUrl"

  val token = "testToken"

  class Setup(withProxy: Boolean) {

    private val buildConnector = new TransactionalConnector {
      override protected def httpProxy = mockHttp
      override protected def httpNoProxy = mockHttp

      override protected val cohoUrl = cohoUrlValue
      override protected val stubUrl = stubUrlValue
      override protected val cohoApiAuthToken = token
      override protected val featureSwitch = mockFeatureSwitch
    }

    private def featureSwitch(enabled: Boolean) = BooleanFeatureSwitch("transactionalAPI", enabled = enabled)

    val connector: TransactionalConnector = {
      when(mockFeatureSwitch.transactionalAPI).thenReturn(featureSwitch(withProxy))
      buildConnector
    }
  }

  "fetchTransactionalData" when {

    val transactionId = "12345"

    "not using the proxy" should {

      val json = Json.parse(
        """
          |{
          | "test":"json"
          |}
        """.stripMargin)

      val fullStubUrl = s"$stubUrlValue/incorporation-frontend-stubs/fetch-data/$transactionId"

      "return a SuccessfulTransactionalAPIResponse with a JsValue" in new Setup(false) {
        when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any())).thenReturn(Future.successful(json))

        val result = await(connector.fetchTransactionalData(transactionId))

        result shouldBe SuccessfulTransactionalAPIResponse(json)
      }

      "return a FailedTransactionalAPIResponse" when {

        "a 404 NotFound status code is returned" in new Setup(false) {
          val response = Future.failed(new NotFoundException(""))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }

        "any other http exception status (4xx / 5xx) is returned" in new Setup(false) {
          val response = Future.failed(new HttpException("Any http status exception that extends this class is either 4xx or 5xx", 9999))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }

        "any Throwable is caught" in new Setup(false) {
          val response = Future.failed(new Throwable(""))

          when(mockHttp.GET[JsValue](eqTo(fullStubUrl))(any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }
      }
    }

    "using a proxy" should {

      val json = Json.parse(
        """
          |{
          | "test":"json"
          |}
        """.stripMargin)


      val fullCohoUrl = s"$cohoUrlValue/submissionData/$transactionId"

      "return a SuccessfulTransactionalAPIResponse with a JsValue" in new Setup(true) {
        when(mockHttp.GET[JsValue](eqTo(fullCohoUrl))(any(), any())).thenReturn(Future.successful(json))

        val result = await(connector.fetchTransactionalData(transactionId))

        result shouldBe SuccessfulTransactionalAPIResponse(json)
      }

      "return a FailedTransactionalAPIResponse" when {

        "a 404 NotFound status code is returned" in new Setup(true) {
          val response = Future.failed(new NotFoundException(""))

          when(mockHttp.GET[JsValue](eqTo(fullCohoUrl))(any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }

        "any other http exception status (4xx / 5xx) is returned" in new Setup(true) {
          val response = Future.failed(new HttpException("Any http status exception that extends this class is either 4xx or 5xx", 9999))

          when(mockHttp.GET[JsValue](eqTo(fullCohoUrl))(any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }

        "any Throwable is caught" in new Setup(true) {
          val response = Future.failed(new Throwable(""))

          when(mockHttp.GET[JsValue](eqTo(fullCohoUrl))(any(), any())).thenReturn(response)

          val result = await(connector.fetchTransactionalData(transactionId))

          result shouldBe FailedTransactionalAPIResponse
        }
      }
    }
  }
}
