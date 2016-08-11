package net.ssanj.sbtscuggest

import sbt._
import sbt.std.TaskStreams
import java.io.File

object ScuggestKeys {
  val scuggestSrcDirs = SettingKey[Seq[File]]("scuggest-src-dirs", "The source directories that scuggest uses for imports")
  val scuggestFilters = SettingKey[Seq[String]]("scuggest-filters", "Paths that are not consider during an import search")
  val scuggest        = TaskKey[Unit]("scuggest", "Generates import entries for your Sublime Text project.")
}

object SbtScuggest extends AutoPlugin {

  override lazy val projectSettings = Seq(
      ScuggestKeys.scuggestFilters in ThisBuild := Seq("sun", "com/sun"),

      ScuggestKeys.scuggestSrcDirs <<= (Keys.scalaSource in Compile, Keys.scalaSource in Test){ (srcDir, testDir) =>
        Seq(srcDir, testDir)
      },

      ScuggestKeys.scuggest <<= (Keys.state, ScuggestKeys.scuggestSrcDirs, ScuggestKeys.scuggestFilters, Keys.streams) map scuggest
  )

  def scuggest(state: State, srcDirs: Seq[File], filters: Seq[String], streams: TaskStreams[_]) {
    val log = streams.log
    log.info("scuggest called!")
    // val extracted = Project.extract(state)
    // val buildStruct = extracted.structure

    // log.info(s"Grabbing all dependency source jars. This may take a while if you don't have them in your Ivy cache")
    // EvaluateTask(buildStruct, Keys.updateClassifiers, state, extracted.currentRef).fold(state)(Function.tupled { (state, result) =>
    //   result match {
    //     case Value(updateReport) =>
    //       log.info(s"Clearing $dependencySrcUnzipDir")
    //       // TODO this could be pretty bad if someone overrides the install dir with an important dir
    //       sbt.IO.delete(dependencySrcUnzipDir)
    //       log.info(s"Unzipping dependency source jars into $dependencySrcUnzipDir")
    //       for {
    //         configuration <- updateReport.configurations
    //         module <- configuration.modules
    //         sourceArtifact <- module.artifacts if sourceArtifact._1.`type` == "src"
    //       } {
    //         val (artifact, file) = sourceArtifact
    //         sbt.IO.unzip(file, dependencySrcUnzipDir, srcFileFilter)
    //       }
    //       val existingCtagsSrcDirs = ctagsSrcDirs.filter(_.exists)
    //       log.debug(s"existing ctags src dirs: $existingCtagsSrcDirs")
    //       log.info(s"Generating tag file")
    //       ctagsGeneration(CtagsGenerationContext(ctagsParams, existingCtagsSrcDirs, buildStruct, streams.log))
    //       state
    //     case x =>
    //       log.error(s"error trying to update classifiers to find source jars: $x")
    //       state
    //   }
    // })
  }

}
