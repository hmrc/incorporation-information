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

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json


sealed trait FeatureSwitch {
  def name: String
  def enabled: Boolean
}

trait TimedFeatureSwitch extends FeatureSwitch {

  def start: Option[DateTime]
  def end: Option[DateTime]
  def target: DateTime

  override def enabled: Boolean = (start, end) match {
    case (Some(s), Some(e)) => !target.isBefore(s) && !target.isAfter(e)
    case (None, Some(e)) => !target.isAfter(e)
    case (Some(s), None) => !target.isBefore(s)
    case (None, None) => false
  }
}

case class BooleanFeatureSwitch(name: String, enabled: Boolean) extends FeatureSwitch

case class EnabledTimedFeatureSwitch(name: String, start: Option[DateTime], end: Option[DateTime], target: DateTime) extends TimedFeatureSwitch
case class DisabledTimedFeatureSwitch(name: String, start: Option[DateTime], end: Option[DateTime], target: DateTime) extends TimedFeatureSwitch {
  override def enabled = !super.enabled
}


object FeatureSwitch {

  val DisabledIntervalExtractor = """!(\S+)_(\S+)""".r
  val EnabledIntervalExtractor = """(\S+)_(\S+)""".r
  val UNSPECIFIED = "X"
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis()

  private[utils] def getProperty(name: String): FeatureSwitch = {
    val value = sys.props.get(systemPropertyName(name))

    value match {
      case Some("true") => BooleanFeatureSwitch(name, enabled = true)
      case Some(DisabledIntervalExtractor(start, end)) => DisabledTimedFeatureSwitch(name, toDate(start), toDate(end), DateTime.now(DateTimeZone.UTC))
      case Some(EnabledIntervalExtractor(start, end)) => EnabledTimedFeatureSwitch(name, toDate(start), toDate(end), DateTime.now(DateTimeZone.UTC))
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

  private[utils] def systemPropertyName(name: String) = {
    val seqOfJobs = Seq(
      SCRSFeatureSwitches.KEY_INCORP_UPDATE,
      SCRSFeatureSwitches.KEY_FIRE_SUBS,
      SCRSFeatureSwitches.KEY_PRO_MONITORING,
      SCRSFeatureSwitches.KEY_SCHED_METRICS)
    if (seqOfJobs.contains(name)) {
      s"schedules.$name.enabled"
    } else {
      s"feature.$name"
    }
  }

  def enable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "true")
  def disable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "false")

  def apply(name: String, enabled: Boolean = false): FeatureSwitch = getProperty(name)
  def unapply(fs: FeatureSwitch): Option[(String, Boolean)] = Some(fs.name -> fs.enabled)

}

object SCRSFeatureSwitches extends SCRSFeatureSwitches {
  val KEY_TX_API = "transactionalAPI"
  val KEY_INCORP_UPDATE = "incorp-update-job"
  val KEY_FIRE_SUBS = "fire-subs-job"
  val KEY_SCHED_METRICS = "metrics-job"
  val KEY_PRO_MONITORING = "proactive-monitoring-job"
}

trait SCRSFeatureSwitches {

  val KEY_TX_API: String
  val KEY_INCORP_UPDATE: String
  val KEY_FIRE_SUBS: String
  val KEY_SCHED_METRICS: String
  val KEY_PRO_MONITORING: String

  def transactionalAPI = FeatureSwitch.getProperty(KEY_TX_API)
  def scheduler = FeatureSwitch.getProperty(KEY_INCORP_UPDATE)
  def fireSubs = FeatureSwitch.getProperty(KEY_FIRE_SUBS)
  def scheduledMetrics = FeatureSwitch.getProperty(KEY_SCHED_METRICS)
  def proactiveMonitoring: FeatureSwitch = FeatureSwitch.getProperty(KEY_PRO_MONITORING)

  def apply(name: String): Option[FeatureSwitch] = name match {
    case KEY_TX_API => Some(transactionalAPI)
    case KEY_INCORP_UPDATE => Some(scheduler)
    case KEY_FIRE_SUBS => Some(fireSubs)
    case KEY_SCHED_METRICS => Some(scheduledMetrics)
    case KEY_PRO_MONITORING => Some(proactiveMonitoring)
    case _ => None
  }
}
