/*
 * Copyright 2019 HM Revenue & Customs
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



import org.asynchttpclient.util.{Base64 => libBase64}

import scala.util.{Failure, Success, Try}

object Base64 {

  def decode(str: String): String = {
    Try(libBase64.decode(str)) match {
      case Success(decoded) => new String(decoded)
      case Failure(_) => throw new RuntimeException(s"$str was not base64 encoded correctly or at all")
    }
  }

  def encode(str: String): String = libBase64.encode(str.toCharArray.map(_.toByte))
}
