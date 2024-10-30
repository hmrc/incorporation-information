
import sbt.*

object AppDependencies {

  import play.sbt.PlayImport.*
  import play.core.PlayVersion

  private val playVersion = "-play-30"
  private val bootstrapPlayVersion  =  "8.6.0"
  private val bootstrapVersion = "7.13.0"
  private val hmrcMongoVersion = "1.9.0"
  private val scalaTestVersion = "3.2.18"
  private val pekkoVersion                =  "1.2.0-pekko-1.0.x"
  private val scalatestPlusPlayVersion    =  "7.0.1"
  private val flexmarkVersion             =  "0.64.0"
  private val wiremockVersion             =  "3.5.4"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "io.github.samueleresca"    %%  "pekko-quartz-scheduler"  % pekkoVersion,
    "uk.gov.hmrc"       %% s"bootstrap-backend$playVersion"   % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo$playVersion"          % hmrcMongoVersion,
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"               %% s"bootstrap-test$playVersion"    % bootstrapPlayVersion      % Test,
    "org.scalatest"             %%  "scalatest"                     % scalaTestVersion          % Test,
    "org.scalatestplus.play"    %%  "scalatestplus-play"            % scalatestPlusPlayVersion  % Test,
    "com.vladsch.flexmark"      %   "flexmark-all"                  % flexmarkVersion           % Test,
    "uk.gov.hmrc.mongo"         %% s"hmrc-mongo-test$playVersion"   % hmrcMongoVersion          % Test,
    "org.playframework"         %%  "play-test"                     % PlayVersion.current       % Test,
    "org.scalatestplus"         %%  "mockito-4-5"                   % "3.2.12.0"                % Test,
    "org.scalatestplus"         %%  "scalacheck-1-17"               % "3.2.18.0"                % Test,
    "org.wiremock"              %   "wiremock-standalone"           % wiremockVersion           % Test

  )

  def apply(): Seq[sbt.ModuleID] = compile ++ test

}