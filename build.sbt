name := "sbt-scuggest"

organization := "net.ssanj"

version := "0.0.3.7"

scalaVersion := "2.10.6"

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8"
)

sbtPlugin := true

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.8"
