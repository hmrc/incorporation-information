import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "-play-28"
  private val bootstrapVersion = "6.4.0"
  private val hmrcMongoVersion = "0.71.0"
  private val scalaTestVersion = "3.2.12"

  val compile = Seq(
    ws,
    "com.enragedginger" %%  "akka-quartz-scheduler"           % "1.9.2-akka-2.6.x",
    "uk.gov.hmrc"       %% s"bootstrap-backend$playVersion"   % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo$playVersion"          % hmrcMongoVersion,
  )

  def test(scope: String = "test,it") = Seq(
    "org.scalatest"             %%  "scalatest"                   % scalaTestVersion % scope,
    "org.scalatestplus.play"    %%  "scalatestplus-play"          % "5.1.0" % scope,
    "com.vladsch.flexmark"      %   "flexmark-all"                % "0.62.2" % scope,
    "com.github.tomakehurst"    %   "wiremock-jre8"               % "2.27.2" % scope,
    "com.typesafe.play"         %%  "play-test"                   % PlayVersion.current % scope,
    "org.scalatestplus"         %%  "mockito-4-5"                 % s"$scalaTestVersion.0" % scope,
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-test$playVersion" % hmrcMongoVersion % scope
  )

}