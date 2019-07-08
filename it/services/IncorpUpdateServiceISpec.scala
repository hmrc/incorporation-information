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

import helpers.IntegrationSpecBase
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import repositories.{IncorpUpdateMongo, IncorpUpdateMongoRepository, QueueMongo, QueueMongoRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoSpecSupport
import reactivemongo.play.json.ImplicitBSONHandlers._

import scala.concurrent.ExecutionContext.Implicits.global

class IncorpUpdateServiceISpec extends IntegrationSpecBase with MongoSpecSupport {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig())
    .build()

  trait Setup {
    val incorpInfoRepo: IncorpUpdateMongoRepository = app.injector.instanceOf[IncorpUpdateMongo].repo
    val incorpQueueRepo: QueueMongoRepository = app.injector.instanceOf[QueueMongo].repo

    await(incorpInfoRepo.drop)
    await(incorpInfoRepo.ensureIndexes)

    await(incorpQueueRepo.drop)
    await(incorpQueueRepo.ensureIndexes)

    val service: IncorpUpdateService = app.injector.instanceOf[IncorpUpdateService]
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "updateSpecificIncorpUpdateByTP" should {

    val transactionId = "trans-12345"
    val timepointToFetch = "123"
    val timepoint = "124"
    val timepointList = Seq(timepointToFetch)

    val incorpDate = "2016-08-10"

    val existingIncorpInfoNoIncorpDate = Json.parse(
      s"""
        |{
        |   "_id" : "$transactionId",
        |   "transaction_status" : "accepted",
        |   "company_number" : "9999999999",
        |   "timepoint" : "$timepoint"
        |}
      """.stripMargin)

    val newIncorpInfo = Json.parse(
      s"""
        |{
        |   "_id" : "$transactionId",
        |   "transaction_status" : "accepted",
        |   "company_number" : "9999999999",
        |   "incorporated_on":"$incorpDate",
        |   "timepoint" : "$timepoint"
        |}
      """.stripMargin)

    val existingQueueItemNoIncorpDate = Json.parse(
      s"""
        |{
        |   "timestamp" : 1510148919784,
        |   "incorp_update" : {
        |       "transaction_id" : "$transactionId",
        |       "transaction_status" : "accepted",
        |       "company_number" : "9999999999",
        |       "timepoint" : "$timepoint"
        |   }
        |}
      """.stripMargin)

    val newQueueItem = Json.parse(
      s"""
         |{
         |   "timestamp" : 1510148919784,
         |   "incorp_update" : {
         |       "transaction_id" : "$transactionId",
         |       "transaction_status" : "accepted",
         |       "company_number" : "9999999999",
         |       "incorporated_on":"$incorpDate",
         |       "timepoint" : "$timepoint"
         |   }
         |}
      """.stripMargin).as[JsObject]

    val incorpInfoResponse = Json.parse(
      s"""{
        |"items":[
        | {
        |   "company_number":"9999999999",
        |   "transaction_status":"accepted",
        |   "transaction_type":"incorporation",
        |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
        |   "transaction_id":"$transactionId",
        |   "incorporated_on":"2016-08-10",
        |   "timepoint":"$timepoint"
        | }
        |],
        |"links":{
        | "next":"https://ewf.companieshouse.gov.uk/internal/check-submission?timepoint=123456789"
        |}
        |}""".stripMargin)

    "update an IncorpInfo and Queue entry that doesn't have an incorp update with one that does" in new Setup {

      implicit def writesToOWrites[T](implicit w: Writes[T]): OWrites[T] = new OWrites[T] {
        override def writes(o: T): JsObject = Json.toJson(o)(w).as[JsObject]
      }

      implicit class richJsObject(o: JsObject) {
        def withoutTimestamp: JsObject = o - "timestamp"
        def withoutOID: JsObject = o - "_id"
      }

      stubGet(s"/incorporation-frontend-stubs/submissions\\?timepoint=$timepointToFetch&items_per_page=1", 200, incorpInfoResponse.toString())

      await(incorpInfoRepo.collection.insert(false).one(existingIncorpInfoNoIncorpDate))
      await(incorpInfoRepo.count) shouldBe 1

      await(incorpQueueRepo.collection.insert(false).one(existingQueueItemNoIncorpDate))
      await(incorpQueueRepo.count) shouldBe 1

      val result: Seq[Boolean] = service.updateSpecificIncorpUpdateByTP(timepointList)

      result shouldBe List(true)

      await(incorpInfoRepo.count) shouldBe 1
      await(incorpQueueRepo.count) shouldBe 1

      await(incorpInfoRepo.collection.find(Json.obj(),Option.empty)(JsObjectDocumentWriter, JsObjectDocumentWriter).one[JsObject]) shouldBe Some(newIncorpInfo)

      val Some(queueItem) = await(incorpQueueRepo.collection.find(Json.obj(), Option.empty)(JsObjectDocumentWriter, JsObjectDocumentWriter).one[JsObject])

      queueItem.withoutTimestamp.withoutOID shouldBe newQueueItem.withoutTimestamp.withoutOID
    }
  }
}
