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

package services

import helpers.IntegrationSpecBase
import models.{IncorpUpdate, QueuedIncorpUpdate}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.Helpers._
import repositories.{IncorpUpdateMongo, IncorpUpdateMongoRepository, QueueMongo, QueueMongoRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

class IncorpUpdateServiceISpec extends IntegrationSpecBase with MongoSupport {

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(fakeConfig())
    .build()

  lazy val incorpInfoRepo: IncorpUpdateMongoRepository = app.injector.instanceOf[IncorpUpdateMongo].repo
  lazy val incorpQueueRepo: QueueMongoRepository = app.injector.instanceOf[QueueMongo].repo

  trait Setup {

    await(incorpInfoRepo.collection.drop().toFuture())
    await(incorpInfoRepo.ensureIndexes)

    await(incorpQueueRepo.collection.drop().toFuture())
    await(incorpQueueRepo.ensureIndexes)

    val service: IncorpUpdateService = app.injector.instanceOf[IncorpUpdateService]
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "updateSpecificIncorpUpdateByTP" must {

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

      await(incorpInfoRepo.storeSingleIncorpUpdate(existingIncorpInfoNoIncorpDate.as[IncorpUpdate](IncorpUpdate.mongoFormat)))
      await(incorpInfoRepo.collection.countDocuments().toFuture()) mustBe 1

      await(incorpQueueRepo.upsertIncorpUpdate(existingQueueItemNoIncorpDate.as[QueuedIncorpUpdate]))
      await(incorpQueueRepo.collection.countDocuments().toFuture()) mustBe 1

      val result: Seq[Boolean] = await(service.updateSpecificIncorpUpdateByTP(timepointList))

      result mustBe List(true)

      await(incorpInfoRepo.collection.countDocuments().toFuture()) mustBe 1
      await(incorpQueueRepo.collection.countDocuments().toFuture()) mustBe 1

      await(incorpInfoRepo.collection.find.headOption()) mustBe Some(newIncorpInfo.as[IncorpUpdate](IncorpUpdate.mongoFormat))

      val queueItem = await(incorpQueueRepo.collection.find().head())

      Json.toJson(queueItem).as[JsObject].withoutTimestamp.withoutOID mustBe newQueueItem.withoutTimestamp.withoutOID
    }
  }
}
