
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, integrationTestSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

lazy val plugins : Seq[Plugins] = Seq(play.sbt.PlayScala)
lazy val playSettings : Seq[Setting[_]] = Seq.empty

val appName = "incorporation-information"

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys

  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;config.*;.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimum := 80,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala,SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins : _*)
  .settings(playSettings ++ scoverageSettings : _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test(),
    retrieveManaged := true,
    evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    scalaVersion := "2.12.13",
    resolvers += Resolver.jcenterRepo
  )
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(IntegrationTest / javaOptions += "-Dlogger.resource=logback-test.xml")
  .settings(integrationTestSettings())
  .settings(majorVersion := 1)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427

