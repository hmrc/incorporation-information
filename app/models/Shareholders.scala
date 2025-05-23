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

package models

import utils.Logging
import play.api.libs.json._

object Shareholders extends Logging {
  val reads = new Reads[Option[JsArray]] {
    override def reads(json: JsValue): JsResult[Option[JsArray]] = {
      val j = json \ "shareholders"
      JsSuccess(j.validateOpt[JsArray].fold[Option[JsArray]](_ => {
        logger.error("[ShareholdersReads] 'shareholders' is not an array")
        Option.empty
      }, identity))
    }
  }
}