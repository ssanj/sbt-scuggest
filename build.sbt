name := "sbt-scuggest"

organization := "net.ssanj"

version := "0.0.8.0-SNAPSHOT"

scalaVersion := "2.12.3"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8"
)

sbtPlugin := true

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.3"

publishMavenStyle := false

sbtVersion in Global := "1.0.3"

scalaCompilerBridgeSource := {
  val sv = appConfiguration.value.provider.id.version
  ("org.scala-sbt" % "compiler-interface" % sv % "component").sources
}

crossSbtVersions := Vector("0.13.16", "1.0.3")