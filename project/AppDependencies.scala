import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  private val playVersion = "-play-28"
  private val bootstrapVersion = "7.13.0"
  private val hmrcMongoVersion = "0.74.0"
  private val scalaTestVersion = "3.2.15"

  val compile = Seq(
    ws,
    "com.enragedginger" %%  "akka-quartz-scheduler"           % "1.9.3-akka-2.6.x",
    "uk.gov.hmrc"       %% s"bootstrap-backend$playVersion"   % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo$playVersion"          % hmrcMongoVersion,
  )

  val test = Seq(
    "uk.gov.hmrc"               %% s"bootstrap-test$playVersion"  % bootstrapVersion        % "test,it",
    "org.scalatest"             %%  "scalatest"                   % scalaTestVersion        % "test,it",
    "org.scalatestplus.play"    %%  "scalatestplus-play"          % "5.1.0"                 % "test,it",
    "com.vladsch.flexmark"      %   "flexmark-all"                % "0.64.0"                % "test,it",
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-test$playVersion" % hmrcMongoVersion        % "test,it",
    "com.typesafe.play"         %%  "play-test"                   % PlayVersion.current     % "test,it",
    "org.scalatestplus"         %%  "mockito-4-5"                 % s"3.2.12.0"  % "test",
    "com.github.tomakehurst"    %   "wiremock-jre8-standalone"    % "2.35.0"                % "it"
  )

  def apply(): Seq[ModuleID] = compile ++ test

}