name := "sbt-scuggest"

organization := "net.ssanj"

version := "0.0.4.1"

scalaVersion := "2.10.6"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8"
)

sbtPlugin := true

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.8"

publishMavenStyle := true

pomExtra := (
  <developers>
    <developer>
    <id>ssanj</id>
    <name>Sanjiv Sahayam</name>
      <url>https://github.com/ssanj</url>
    </developer>
  </developers>)