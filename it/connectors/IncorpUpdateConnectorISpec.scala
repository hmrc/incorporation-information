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

import helpers.IntegrationSpecBase
import models.IncorpUpdate
import org.joda.time.DateTime
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.play.http.{BadRequestException, HeaderCarrier}

class IncorpUpdateConnectorISpec extends IntegrationSpecBase {

  val additionalConfiguration = Map(
    "microservice.services.incorp-frontend-stubs.host" -> wiremockHost,
    "microservice.services.incorp-frontend-stubs.port" -> wiremockPort,
    "microservice.services.incorp-update-api.url" -> "N/A",
    "microservice.services.incorp-update-api.token" -> "N/A"
  )

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .build


  "fetchTransactionalData" should {

    val transactionId = "12345"

    val destinationUrl = s"/incorporation-frontend-stubs/fetch-data/$transactionId"

    val responseOne = s"""{
                  |"items":[
                  | {
                  |   "company_number":"9999999999",
                  |   "transaction_status":"accepted",
                  |   "transaction_type":"incorporation",
                  |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
                  |   "transaction_id":"$transactionId",
                  |   "incorporated_on":"2016-08-10",
                  |   "timepoint":"123456789"
                  | }
                  |],
                  |"links":{
                  | "next":"https://ewf.companieshouse.gov.uk/submissions?timepoint=123456789"
                  |}
                  |}""".stripMargin


    val date = new DateTime(2016, 8, 10, 0, 0)
    val item1 = IncorpUpdate(transactionId, "accepted", Some("9999999999"), Some(date), "123456789", None)

    "items from the sample response" in {
      stubGet("/incorporation-frontend-stubs/submissions.*", 200, responseOne)

      val connector = app.injector.instanceOf[IncorporationAPIConnector]

      val result = await(connector.checkForIncorpUpdate(None)(HeaderCarrier()))
      result shouldBe Seq(item1)
    }

    "Return no items if a 204 (no content) result is returned" in {
      stubGet("/incorporation-frontend-stubs/submissions.*", 204, "")

      val connector = app.injector.instanceOf[IncorporationAPIConnector]

      val f = connector.checkForIncorpUpdate(None)(HeaderCarrier())
      val r = await(f)
      r shouldBe Seq()
    }

    "Return no items if a 400 (bad request) result is returned" in {
      stubGet("/incorporation-frontend-stubs/submissions.*", 400, "")

      val connector = app.injector.instanceOf[IncorporationAPIConnector]

      val f = connector.checkForIncorpUpdate(None)(HeaderCarrier())

      val failure = intercept[IncorpUpdateAPIFailure]( await(f) )

      failure.ex shouldBe a[BadRequestException]
    }

  }
}