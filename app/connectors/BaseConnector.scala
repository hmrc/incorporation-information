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

package connectors

import utils.Logging

import scala.concurrent.{ExecutionContext, Future}

trait BaseConnector extends Logging {

  def withRecovery[T](response: => Option[T] = None)(functionName: String,
                                                     regId: Option[String] = None,
                                                     txId: Option[String] = None,
                                                     crn: Option[String] = None)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    f recover { case ex: Exception =>
      logger.error(s"[$functionName] Exception of type '${ex.getClass.getSimpleName}' was thrown${logContext(regId, txId, crn)}")
      response.fold(throw ex)(identity)
    }

  def logContext(regId: Option[String] = None, txId: Option[String] = None, crn: Option[String] = None): String = (regId, txId, crn) match {
    case (Some(rId), None, None) => s" for regId: '$rId'"
    case (Some(rId), None, Some(crn)) => s" for regId: '$rId' and crn: '$crn'"
    case (Some(rId), Some(tId), None) => s" for regId: '$rId' and txId: '$tId'"
    case (Some(rId), Some(tId), Some(crn)) => s" for regId: '$rId', txId: '$tId' and crn: '$crn'"
    case (None, Some(tId), None) => s" for txId: '$tId'"
    case (None, None, Some(crn)) => s" for crn: '$crn'"
    case (None, Some(tId), Some(crn)) => s" for txId: '$tId' and crn: '$crn'"
    case _ => ""
  }
}
