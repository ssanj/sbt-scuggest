package net.ssanj.sbtscuggest

import java.io.File
import java.nio.file.Paths
import play.api.libs.json._
import sbt._
import sbt.std.TaskStreams
import scala.util.{Failure, Success, Try}

object SbtScuggest extends AutoPlugin {

  object autoImport  {
    val scuggestClassDirs = SettingKey[Seq[File]]("scuggest-class-dirs", "The target directories that scuggest uses for imports")
    val scuggestFilters   = SettingKey[Seq[String]]("scuggest-filters", "Paths that are not consider during an import search")
    val scuggestSublimeProjName  = SettingKey[String]("scuggest-sublime-proj-name", "the name of the sublime project file")
    val scuggestGen          = TaskKey[Unit]("scuggest-gen", "Generates import entries for your Sublime Text project.")
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
      scuggestFilters in ThisBuild := Seq("sun", "com/sun"),

      scuggestClassDirs <<= (Keys.classDirectory in Compile, Keys.classDirectory in Test){ (srcDir, testDir) =>
        Seq(srcDir, testDir)
      },

      scuggestSublimeProjName <<= (Keys.name)(identity),

      scuggestGen <<= (Keys.state, scuggestSublimeProjName, scuggestClassDirs, scuggestFilters, Keys.streams) map scuggest
  )

  def scuggest(state: State, projectName: String, classDirs: Seq[File], filters: Seq[String], streams: TaskStreams[_]) {
    val log = streams.log
    log.info("scuggest called!")

    val extracted = Project.extract(state)
    val buildStruct = extracted.structure

    log.info(s"project extracted: " + extracted.currentRef)

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
          log.info("project: " + projectName)
          val result =
            updateSublimeProject(new File(new File("."), s"${projectName}.sublime-project") , dependencyFiles.toSeq, classDirs, javaHomeRtJar, filters) match {
              case Left(CouldNotReadFile(file, error)) => s"Could not read project file: ${file.getAbsolutePath} due to: ${error.getMessage}"
              case Left(CouldNotWriteFile(file, error)) => s"Could not write to project file: ${file.getAbsolutePath} due to: ${error.getMessage}"
              case Left(CouldNotParseContent(content, error)) => s"Could not parse project file with content: ${content} due to: ${error.getMessage}"
              case Left(InvalidProjectStructure(json)) => s"Invalid sublime-project structure: ${json}"
              case Right(_) => s"successfully updated ${projectName}.sublime-project"
            }
           log.info(result)
          state
        case x =>
          log.error(s"error trying to update classifiers to find source jars: $x")
          state
      }
    })
  }

  private def readProjectFile(projectFile: File): Either[ProjectLoadError, String] = {
    Try {
      sbt.IO.readLines(projectFile).mkString(System.lineSeparator)
    }.toEither(CouldNotReadFile(projectFile, _))
  }

  private def parseProjectJson(content: String): Either[ProjectLoadError, JsValue] = {
    Try {
      Json.parse(content)
    }.toEither(CouldNotParseContent(content, _))
  }

  private def writeProjectFile(projectFile: File, json: JsValue): Either[ProjectLoadError, Unit] = {
    //_ <- Try(sbt.IO.write(projectFile, updatedJson).toEither.leftMap(CouldNotWriteFile(projectFile,))
    Try {
      println("updated proj json: " + json)
    }.toEither(CouldNotWriteFile(projectFile, _))
  }

  private def defaultProject: Either[ProjectLoadError, String] = {
    Right(emptySublimeProject): Either[ProjectLoadError, String]
  }

  private def updateSublimeProject(projectFile: File,
                           dependencyFiles: Seq[File],
                           classesDirs: Seq[File],
                           javaRt: Option[File],
                           filters: Seq[String]): Either[ProjectLoadError, Unit] = {
      for {
        content     <- if (projectFile.exists()) readProjectFile(projectFile) else defaultProject
        json        <- parseProjectJson(content)
        updatedJson <- addScuggestElements(json, dependencyFiles, classesDirs, javaRt, filters)
        _           <- writeProjectFile(projectFile, updatedJson)
      } yield ()
  }

  private def addScuggestElements(pjson: JsValue, dependencyFiles: Seq[File], classesDirs: Seq[File], javaRt: Option[File], filters: Seq[String]): Either[ProjectLoadError, JsValue] = {
    pjson match {
      case JsObject(_) =>
        val projectJson = pjson.asInstanceOf[JsObject]
            val allDeps = dependencyFiles ++ classesDirs ++ javaRt.toSeq
            val newSettings =
              (projectJson \ "settings").toOption.fold{
                val settings =
                  JsObject(
                    Map("settings" -> JsObject(
                                        Map(
                                          "scuggest_import_path" -> JsArray(allDeps.map(f => JsString(f.getAbsolutePath))),
                                          "scuggest_filtered_path" -> JsArray(filters.map(JsString(_)))
                                        )
                                      )
                    )
                  )

                settings
              }({

                case settings@JsObject(_) =>
                (settings - ("scuggest_import_path") - ("scuggest_filtered_path")) +
                  ("scuggest_import_path" -> JsArray(allDeps.map(f => JsString(f.getAbsolutePath)))) +
                  ("scuggest_filtered_path" -> JsArray(filters.map(JsString(_))))
                case _ => JsObject(Map.empty[String, JsValue])
              })

            Right((projectJson - ("settings")) + ("settings" -> newSettings))

      case other => Left(InvalidProjectStructure(other))
    }
  }
}
