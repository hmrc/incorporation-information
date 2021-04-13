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

package utils

import Helpers.SCRSSpec

class Base64Spec extends SCRSSpec {

  val decodedString = "testString"
  val bas64String = "dGVzdFN0cmluZw=="

  "decode" should {

    "decode the Base64 string into a readable string" in {
      Base64.decode(bas64String) shouldBe decodedString
    }

    "decode a value that as encoded by this util" in {
      val someString = "someString"
      val encoded = Base64.encode(someString)
      Base64.decode(encoded) shouldBe someString
    }

    "throw an exception when the supplied string is not Base64 encoded" in {
      val nonEncodedString = "non-encoded-string"

      val ex = intercept[RuntimeException](Base64.decode(nonEncodedString))

      ex.getMessage shouldBe s"$nonEncodedString was not base64 encoded correctly or at all"
    }
  }

  "encode" should {

    "encode the string to a Base64 string" in {
      Base64.encode(decodedString) shouldBe bas64String
    }
  }

}
