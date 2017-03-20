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

import Helpers.SCRSSpec
import connectors.{FailedTransactionalAPIResponse, SuccessfulTransactionalAPIResponse, TransactionalConnector}
import play.api.libs.json.Json
import org.mockito.Mockito._

import scala.concurrent.Future

class TransactionalServiceSpec extends SCRSSpec {

  val mockTransactionalConnector = mock[TransactionalConnector]

  class Setup {
    val service = new TransactionalService {
      override protected val connector = mockTransactionalConnector
    }
  }

  "extractJson" should {

    "fetch a json sub-document from a successful TransactionalAPIResponse by the supplied key" in new Setup {
      val key = "testKey"
      val json = Json.parse(
        s"""
          |{
          | "$key": {
          |   "test":"json"
          | }
          |}
        """.stripMargin)

      val response = SuccessfulTransactionalAPIResponse(json)

      await(service.extractJson(response, key)) shouldBe Some(Json.parse("""{"test":"json"}""""))
    }

    "return a None when attempting to fetch a sub-document from a successful TransactionalAPIResponse with an un-matched key" in new Setup {
      val key = "testKeyToFail"
      val json = Json.parse(
        s"""
          |{
          | "testKey": {
          |   "test":"json"
          | }
          |}
        """.stripMargin)

      val response = SuccessfulTransactionalAPIResponse(json)

      await(service.extractJson(response, key)) shouldBe None
    }

    "return a None when a FailedTransactionalAPIResponse is returned" in new Setup {
      val key = "testKey"
      val response = FailedTransactionalAPIResponse

      await(service.extractJson(response, key)) shouldBe None
    }
  }

  "fetchCompanyProfile" should {

    val key = "SCRSCompanyProfile"
    val transactionId = "12345"
    def json(key: String = key) = Json.parse(
      s"""
         |{
         | "$key": {
         |   "test":"json"
         | }
         |}
        """.stripMargin)


    "return some Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(json())
      when(mockTransactionalConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))
      await(service.fetchCompanyProfile(transactionId)) shouldBe Some(Json.parse("""{"test":"json"}"""))
    }

    "return None" when {

      "a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
        val response = FailedTransactionalAPIResponse
        when(mockTransactionalConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchCompanyProfile(transactionId)) shouldBe None
      }

      "a SuccessfulTransactionalAPIResponse is returned for the supplied transaction Id but an incorrect sub-document key is provided" in new Setup {
        val keyToFail = "keyToFail"
        val response = SuccessfulTransactionalAPIResponse(json(keyToFail))
        when(mockTransactionalConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchCompanyProfile(transactionId)) shouldBe None
      }
    }
  }

  "fetchOfficerList" should {

    val key = "SCRSCompanyOfficerList"
    val transactionId = "12345"
    def json(key: String = key) = Json.parse(
      s"""
         |{
         | "$key": {
         |   "test":"json"
         | }
         |}
        """.stripMargin)


    "return some Json when a document is retrieved by the supplied transaction Id and the sub-document is fetched by the supplied key" in new Setup {
      val response = SuccessfulTransactionalAPIResponse(json())
      when(mockTransactionalConnector.fetchTransactionalData(transactionId))
        .thenReturn(Future.successful(response))
      await(service.fetchOfficerList(transactionId)) shouldBe Some(Json.parse("""{"test":"json"}"""))
    }

    "return None" when {

      "a FailedTransactionalAPIResponse is returned from the connector" in new Setup {
        val response = FailedTransactionalAPIResponse
        when(mockTransactionalConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchOfficerList(transactionId)) shouldBe None
      }

      "a SuccessfulTransactionalAPIResponse is returned for the supplied transaction Id but an incorrect sub-document key is provided" in new Setup {
        val keyToFail = "keyToFail"
        val response = SuccessfulTransactionalAPIResponse(json(keyToFail))
        when(mockTransactionalConnector.fetchTransactionalData(transactionId))
          .thenReturn(Future.successful(response))
        await(service.fetchOfficerList(transactionId)) shouldBe None
      }
    }
  }
}
