package net.ssanj.sbtscuggest

import java.io.File
import java.net.URI
import java.nio.file.Paths
import play.api.libs.json._
import sbt._
import sbt.std.TaskStreams
import scala.util.{Failure, Success, Try}

object SbtScuggest extends AutoPlugin {

  override def requires = plugins.JvmPlugin

  override def trigger = allRequirements

  object autoImport  {
    val scuggestClassDirs = SettingKey[Seq[File]](
      "scuggest-class-dirs", "The target directories that scuggest uses for imports")

    val scuggestSearchFilters = SettingKey[Seq[String]](
      "scuggest-search-filters", "Paths that are not consider during an import search")

    val scuggestDepFilters = SettingKey[Seq[String]](
      "scuggest-dep-filters", "Dependencies that are not added to the import search list")

    val scuggestSublimeProjName = SettingKey[String](
      "scuggest-sublime-proj-name", "the name of the sublime project file")

    val scuggestSimulate = SettingKey[Boolean](
      "scuggest-simulate", "whether to simulate the updates to the Sublime Text project file. The default is true")

    val scuggestVerbose = SettingKey[Boolean](
      "scuggest-verbose", "verbose mode, which allows you to see what settings and artefacts are being used. The default is false")

    val scuggestGen = TaskKey[Unit](
      "scuggest-gen", "Generates import entries for your Sublime Text project.")

  }

  private val emptySublimeProject =
    """
    |{
    | "folders":
    |  [
    |    {
    |      "path": "."
    |    }
    |  ]
    |}
   """.stripMargin

  import autoImport._

  override lazy val projectSettings = Seq(
      scuggestSearchFilters in ThisBuild := Seq("sun", "com/sun"),

      scuggestClassDirs := {
        Seq((Keys.classDirectory in Compile).value, (Keys.classDirectory in Test).value)
      },

      scuggestSublimeProjName := Keys.name.value,

      scuggestDepFilters in ThisBuild :=
        Seq(
            """test-interface-.+\.jar$""",
            """scala-parser-combinators_.+\.jar$""",
            """scala-reflect-.+\.jar$""",
            """scala-compiler-.+\.jar$""",
            """jline-.+\.jar$""",
            """scala-xml_.+\.jar"""
        ),

      scuggestSimulate in ThisBuild := true,

      scuggestVerbose in ThisBuild := false,

      scuggestGen := scuggest(
                       Keys.state.value,
                       scuggestSublimeProjName.value,
                       scuggestClassDirs.value,
                       scuggestSearchFilters.value,
                       scuggestDepFilters.value,
                       Keys.streams.value,
                       scuggestSimulate.value,
                       scuggestVerbose.value)
  )

  def scuggest(state: State,
               projectName: String,
               classDirs: Seq[File],
               searchFilters: Seq[String],
               scuggestDepFilters: Seq[String],
               streams: TaskStreams[_],
               simulate: Boolean,
               verbose: Boolean) {

    val log = streams.log

    val extracted = Project.extract(state)
    val buildStruct = extracted.structure

    EvaluateTask(buildStruct,
                 Keys.update,
                 state,
                 extracted.currentRef).fold(state)(Function.tupled { (state, result) =>
      result match {
        case Value(updateReport) =>

          val validDepTypes = Seq("bundle", "jar")

          val dependencyFiles: Set[File] = scala.collection.immutable.HashSet() ++ (
              for {
                configuration  <- updateReport.configurations
                module         <- configuration.modules
                sourceArtifact <- module.artifacts if validDepTypes.contains(sourceArtifact._1.`type`)
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


          val depFilterRegs = scuggestDepFilters.map(_.r)
          val filteredDependencyFiles =
            dependencyFiles.filterNot { dep =>
              depFilterRegs.exists(_.findFirstIn(dep.getAbsolutePath).isDefined)
            }

          if (verbose) {
            log.info("simulated: " + simulate)
            log.info("project dir: " + extracted.currentRef.build)
            log.info("deps: \n" + dependencyFiles.map(_.getAbsolutePath()).mkString("\n"))
            log.info("filtered deps: \n" + filteredDependencyFiles.map(_.getAbsolutePath()).mkString("\n"))
            log.info("class dirs: \n" + classDirs.map(_.getAbsolutePath()).mkString("\n"))
            log.info("java: " + javaHomeRtJar.fold("Could not find JAVA_HOME")(_.getAbsolutePath))
            log.info("project name: " + projectName)

            log.info("project file: " + getProjectFile(extracted.currentRef.build, projectName).
              fold(_ => "???", _.getAbsolutePath))
          }

          val result =
            updateSublimeProject(extracted.currentRef.build,
                                 projectName,
                                 filteredDependencyFiles.toSeq,
                                 classDirs,
                                 javaHomeRtJar,
                                 searchFilters,
                                 simulate,
                                 log) match {

              case Left(CouldNotFindProjectDir(projDir, error))            =>
                s"Could not find project directory: $projDir due to: ${error.getMessage}"

              case Left(CouldNotReadFile(file, error))            =>
                s"Could not read project file: ${file.getAbsolutePath} due to: ${error.getMessage}"

              case Left(CouldNotWriteFile(file, error))           =>
                s"Could not write to project file: ${file.getAbsolutePath} due to: ${error.getMessage}"

              case Left(CouldNotParseContent(content, error))     =>
                s"Could not parse project file with content: ${content} due to: ${error.getMessage}"

              case Left(InvalidProjectStructure(json))            =>
                s"Invalid sublime-project structure: ${json}"

              case Left(InvalidSettingsElement(settings, reason)) =>
                s"Invalid settings element: ${settings}, due to: $reason"

              case Right(_) if simulate                           =>
                s"simulation complete"

              case Right(_)                                       =>
                s"successfully updated ${projectName}.sublime-project"

            }
           log.info(result)
          state
        case x =>
          log.error(s"error trying to add scuggest imports to ${projectName}.sublime-project due to : $x")
          state
      }
    })
  }

  private def readProjectFile(projectFile: File): ProjectLoadType[String] = {
    Try {
      sbt.IO.readLines(projectFile).mkString(System.lineSeparator)
    }.toE(CouldNotReadFile(projectFile, _))
  }

  private def parseProjectJson(content: String): ProjectLoadType[JsValue] = {
    Try {
      Json.parse(content)
    }.toE(CouldNotParseContent(content, _))
  }

  private def getProjectFileBackup(projectFile: File): ProjectLoadType[File] =  {
      val currentTime = java.time.LocalDateTime.now
      val filterOut = Seq(':', '-', 'T', '.')
      Right(new File(projectFile.getAbsolutePath + s".${currentTime.toString.filterNot(filterOut.contains)}"))
  }

  private def backupAndWriteProjectFile(projectFile: File, updatedJson: JsValue): ProjectLoadType[Unit] = {
    for {
      backupFile <- getProjectFileBackup(projectFile)
      _          <- Try(sbt.IO.copyFile(projectFile, backupFile)).toE(CouldNotWriteFile(backupFile, _))
      _          <- Try(sbt.IO.write(projectFile, Json.prettyPrint(updatedJson))).toE(CouldNotWriteFile(projectFile, _))
    } yield ()
  }

  private def writeProjectFile(projectFile: File, updatedJson: JsValue): ProjectLoadType[Unit] = {
    Try(sbt.IO.write(projectFile, Json.prettyPrint(updatedJson))).toE(CouldNotWriteFile(projectFile, _))
  }

  private def printProjectFile(log: sbt.Logger, projectFile: File, updatedJson: JsValue): ProjectLoadType[Unit] = {
    log.info(s"----- This is a simulation -----")
    log.info(s"${projectFile} will be updated the following contents:")
    log.info(Json.prettyPrint(updatedJson))
    log.info(s"To update the project with the above contents, 'set scuggestSimulate := false' and run scuggestGen.")
    Right(())
  }

  private def defaultProject: ProjectLoadType[String] = {
    Right(emptySublimeProject): ProjectLoadType[String]
  }

  private def getProjectFile(projectDir: URI, projectName: String): ProjectLoadType[File] = {
    Try {
      new File(projectDir.getPath, s"${projectName}.sublime-project")
    }.toE(CouldNotFindProjectDir(projectDir, _))
  }

  private def updateSublimeProject(projectURI: URI,
                                   projectName: String,
                                   dependencyFiles: Seq[File],
                                   classesDirs: Seq[File],
                                   javaRt: Option[File],
                                   searchFilters: Seq[String],
                                   simulate: Boolean,
                                   log: sbt.Logger): ProjectLoadType[Unit] = {
      for {
        projectFile <- getProjectFile(projectURI, projectName)
        content     <- if (projectFile.exists()) readProjectFile(projectFile) else defaultProject
        json        <- parseProjectJson(content)
        updatedJson <- addScuggestElements(json, dependencyFiles, classesDirs, javaRt, searchFilters)
        _           <- if (simulate) printProjectFile(log, projectFile, updatedJson)
                       else if (projectFile.exists()) backupAndWriteProjectFile(projectFile, updatedJson)
                       else writeProjectFile(projectFile, updatedJson)
      } yield ()
  }

  private def addScuggestElements(pjson: JsValue,
                                  dependencyFiles: Seq[File],
                                  classesDirs: Seq[File],
                                  javaRt: Option[File],
                                  searchFilters: Seq[String]): ProjectLoadType[JsValue] = {
    pjson match {
      case JsObject(_) =>
        val projectJson = pjson.asInstanceOf[JsObject]
            val allDeps = dependencyFiles ++ classesDirs ++ javaRt.toSeq
            val newSettingsE: ProjectLoadType[JsValue] =
              (projectJson \ "settings").toOption.fold{
                val settings =
                  JsObject(
                    Map(
                      "scuggest_import_path" -> JsArray(allDeps.map(f => JsString(f.getAbsolutePath))),
                      "scuggest_filtered_path" -> JsArray(searchFilters.map(JsString(_)))
                    )
                  )

                Right(settings): ProjectLoadType[JsValue]
              }({

                case settings@JsObject(_) =>
                  val updatedSettings =
                  (settings - ("scuggest_import_path") - ("scuggest_filtered_path")) +
                    ("scuggest_import_path" -> JsArray(allDeps.map(f => JsString(f.getAbsolutePath)))) +
                    ("scuggest_filtered_path" -> JsArray(searchFilters.map(JsString(_))))

                  Right(updatedSettings): ProjectLoadType[JsValue]

                case jsv =>
                  Left(
                    InvalidSettingsElement(jsv, "settings should be a JsObject.")
                  ): ProjectLoadType[JsValue]
              })

              newSettingsE.map { newSettings =>
                (projectJson - ("settings")) + ("settings" -> newSettings)
              }

      case other => Left(InvalidProjectStructure(other))
    }
  }
}
