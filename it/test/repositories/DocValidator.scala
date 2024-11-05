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

package test.repositories

import org.mongodb.scala.Document
import org.mongodb.scala.bson.BsonDocument
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.Future

trait DocValidator extends MongoSupport {

  def validateCRN(regex: String = "^bar[1-7]$"): Future[Document] = {

    val commandDoc = BsonDocument(
      "collMod" -> "incorporation-information",
      "validator" -> BsonDocument("company_number" -> BsonDocument("$regex" -> regex))
    )

    mongoComponent.database.runCommand(commandDoc).toFuture()
  }
}
