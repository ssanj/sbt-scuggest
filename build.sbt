name := "sbt-scuggest"

organization := "net.ssanj"

version := "0.0.6.0-SNAPSHOT"

scalaVersion := "2.10.6"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8"
)

sbtPlugin := true

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.3"

publishMavenStyle := false
