import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "incorporation-information"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0",
    "uk.gov.hmrc" %% "microservice-bootstrap" % "6.18.0",
    "uk.gov.hmrc" %% "domain" % "5.2.0",
    "uk.gov.hmrc" %% "play-scheduling" % "4.1.0",
    "uk.gov.hmrc" %% "mongo-lock" % "4.1.0"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.1.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.6.0" % "it",
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope,
    "org.mockito" % "mockito-all" % "2.0.2-beta" % scope
  )
}
