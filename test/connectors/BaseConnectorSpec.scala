/*
 * Copyright 2023 HM Revenue & Customs
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

class BaseConnectorSpec extends SCRSSpec {

  val regId = "reg12345"
  val txId = "tx12345"
  val crn = "crn12345"

  object TestBaseConnector extends BaseConnector

  "BaseConnector" when {

    "calling .logContext(regId: Option[String], txId: Option[String], crn: Option[String])" must {

      "construct correct log meta when all ID's are provided" in {
        TestBaseConnector.logContext(Some(regId), Some(txId), Some(crn)) mustBe s" for regId: '$regId', txId: '$txId' and crn: '$crn'"
      }

      "construct correct log meta when regId and txID are provided" in {
        TestBaseConnector.logContext(Some(regId), Some(txId), None) mustBe s" for regId: '$regId' and txId: '$txId'"
      }

      "construct correct log meta when regId and crn are provided" in {
        TestBaseConnector.logContext(Some(regId), None, Some(crn)) mustBe s" for regId: '$regId' and crn: '$crn'"
      }

      "construct correct log meta when txId and crn are provided" in {
        TestBaseConnector.logContext(None, Some(txId), Some(crn)) mustBe s" for txId: '$txId' and crn: '$crn'"
      }

      "construct correct log meta when regId only is provided" in {
        TestBaseConnector.logContext(Some(regId), None, None) mustBe s" for regId: '$regId'"
      }

      "construct correct log meta when txID only provided" in {
        TestBaseConnector.logContext(None, Some(txId), None) mustBe s" for txId: '$txId'"
      }

      "construct correct log meta when crn only provided" in {
        TestBaseConnector.logContext(None, None, Some(crn)) mustBe s" for crn: '$crn'"
      }

      "construct no meta when no ID's provided" in {
        TestBaseConnector.logContext(None, None, None) mustBe ""
      }
    }
  }
}
