import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "incorporation-information"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x",
    "uk.gov.hmrc" %% "bootstrap-play-25" % "4.9.0",
    "uk.gov.hmrc" %% "domain" % "5.3.0",
    "uk.gov.hmrc" %% "mongo-lock" % "6.10.0-play-25",
    "uk.gov.hmrc" %% "simple-reactivemongo" % "7.12.0-play-25" force()
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.5.0-play-25" % scope,
    "org.scalatest" %% "scalatest" % "3.0.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.21.0" % "it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.7.0-play-25" % scope
  )
}