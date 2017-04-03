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

package utils

import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.Json


sealed trait FeatureSwitch {
  def name: String
  def enabled: Boolean
}

case class BooleanFeatureSwitch(name: String, enabled: Boolean) extends FeatureSwitch

case class TimedFeatureSwitch(name: String, start: Option[DateTime], end: Option[DateTime], target: DateTime) extends FeatureSwitch {

  override def enabled: Boolean = (start, end) match {
    case (Some(s), Some(e)) => !target.isBefore(s) && !target.isAfter(e)
    case (None, Some(e)) => !target.isAfter(e)
    case (Some(s), None) => !target.isBefore(s)
    case (None, None) => false
  }
}

object FeatureSwitch {

  val DatesIntervalExtractor = """(\S+)_(\S+)""".r
  val UNSPECIFIED = "X"
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis()

  private[utils] def getProperty(name: String): FeatureSwitch = {
    val value = sys.props.get(systemPropertyName(name))

    value match {
      case Some("true") => BooleanFeatureSwitch(name, enabled = true)
      case Some(DatesIntervalExtractor(start, end)) => TimedFeatureSwitch(name, toDate(start), toDate(end), DateTime.now(DateTimeZone.UTC))
      case _ => BooleanFeatureSwitch(name, enabled = false)
    }
  }

  private[utils] def setProperty(name: String, value: String): FeatureSwitch = {
    sys.props += ((systemPropertyName(name), value))
    getProperty(name)
  }

  private[utils] def toDate(text: String) : Option[DateTime] = {
    text match {
      case UNSPECIFIED => None
      case _ => Some(dateFormat.parseDateTime(text))
    }
  }

  private[utils] def systemPropertyName(name: String) = s"feature.$name"

  def enable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "true")
  def disable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "false")

  def apply(name: String, enabled: Boolean = false): FeatureSwitch = getProperty(name)
  def unapply(fs: FeatureSwitch): Option[(String, Boolean)] = Some(fs.name -> fs.enabled)

  implicit val formats = Json.format[FeatureSwitch]
}

object SCRSFeatureSwitches extends SCRSFeatureSwitches {
  val KEY_TX_API = "transactionalAPI"
  val KEY_INCORP_UPDATE = "incorpUpdate"
}

trait SCRSFeatureSwitches {

  val KEY_TX_API: String
  val KEY_INCORP_UPDATE: String

  def transactionalAPI = FeatureSwitch.getProperty(KEY_TX_API)
  def scheduler = FeatureSwitch.getProperty(KEY_INCORP_UPDATE)

  def apply(name: String): Option[FeatureSwitch] = name match {
    case KEY_TX_API => Some(transactionalAPI)
    case KEY_INCORP_UPDATE => Some(scheduler)
    case _ => None
  }
}
