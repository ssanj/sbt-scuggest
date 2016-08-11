package net.ssanj.sbtscuggest

import sbt._
import sbt.std.TaskStreams
import java.io.File
import java.nio.file.Paths

object ScuggestKeys {
  val scuggestClassDirs = SettingKey[Seq[File]]("scuggest-class-dirs", "The target directories that scuggest uses for imports")
  val scuggestFilters = SettingKey[Seq[String]]("scuggest-filters", "Paths that are not consider during an import search")
  val scuggest        = TaskKey[Unit]("scuggest", "Generates import entries for your Sublime Text project.")
}

object SbtScuggest extends AutoPlugin {

  override lazy val projectSettings = Seq(
      ScuggestKeys.scuggestFilters in ThisBuild := Seq("sun", "com/sun"),

      ScuggestKeys.scuggestClassDirs <<= (Keys.classDirectory in Compile, Keys.classDirectory in Test){ (srcDir, testDir) =>
        Seq(srcDir, testDir)
      },

      ScuggestKeys.scuggest <<= (Keys.state, ScuggestKeys.scuggestClassDirs, ScuggestKeys.scuggestFilters, Keys.streams) map scuggest
  )

  def scuggest(state: State, classDirs: Seq[File], filters: Seq[String], streams: TaskStreams[_]) {
    val log = streams.log
    log.info("scuggest called!")

    val extracted = Project.extract(state)
    val buildStruct = extracted.structure

    EvaluateTask(buildStruct, Keys.updateClassifiers, state, extracted.currentRef).fold(state)(Function.tupled { (state, result) =>
      result match {
        case Value(updateReport) =>

          val dependencyFiles: Set[File] = scala.collection.immutable.HashSet() ++ (
              for {
                configuration  <- updateReport.configurations
                module         <- configuration.modules
                sourceArtifact <- module.artifacts if sourceArtifact._1.`type` == "jar"
              } yield sourceArtifact._2
          )

          val javaHome = Option(System.getenv("JAVA_HOME"))
          val javaHomeRtJar =
            javaHome.flatMap { jh =>
              val jdk = Paths.get(jh, "jre", "lib", "rt.jar").toFile
              val jre = Paths.get(jh, "lib", "rt.jar").toFile

              if (jdk.exists()) Option(jdk)
              else if (jre.exists()) Option(jre)
              else None
            }

          log.info("deps: " + dependencyFiles.map(_.getAbsolutePath()).mkString("\n"))
          log.info("class dirs: " + classDirs.map(_.getAbsolutePath()).mkString("\n"))
          log.info("java: " + javaHomeRtJar.fold("Could not find JAVA_HOME")(_.getAbsolutePath))
          state
        case x =>
          log.error(s"error trying to update classifiers to find source jars: $x")
          state
      }
    })
  }

}
