import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.1-akka-2.5.x",
    "uk.gov.hmrc" %% "bootstrap-backend-play-26" % "5.1.0",
    "uk.gov.hmrc" %% "domain" % "5.11.0-play-26",
    "uk.gov.hmrc" %% "mongo-lock" % "7.0.0-play-26",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "8.0.0-play-26",
    "com.typesafe.play" %% "play-json-joda" % "2.6.10"
  )

  def test(scope: String = "test,it") = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.github.tomakehurst" % "wiremock-jre8" % "2.27.2" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.mockito" % "mockito-core" % "3.9.0" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "5.0.0-play-26" % scope
  )

}