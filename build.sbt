
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, integrationTestSettings, scalaSettings}

val appName = "incorporation-information"

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys

  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;models/.data/..*;view.*;config.*;.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
  .settings(scoverageSettings : _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    scalacOptions += "-Xlint:-unused",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    scalaVersion := "2.13.8",
    resolvers += Resolver.jcenterRepo
  )
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(IntegrationTest / javaOptions += "-Dlogger.resource=logback-test.xml")
  .settings(integrationTestSettings())
  .settings(majorVersion := 1)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427

