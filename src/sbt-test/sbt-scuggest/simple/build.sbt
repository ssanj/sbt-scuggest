import play.api.libs.json._

def trueOrError(cond: Boolean, error: String): Unit = if (cond) {} else sys.error(error)

lazy val root = (project in file("."))
  .settings(
    name := "simple",
    version := "0.1",
    scalaVersion := "2.10.6",
    TaskKey[Unit]("verify-sublime-proj") := {
      val process = Process("cat", Seq(baseDirectory.value / name.value + ".sublime-project"))
      val out = (process!!)
      val json = Json.parse(out).asInstanceOf[JsObject]

      (json \ "settings" \ "scuggest_import_path").toOption.fold(
        sys.error("could not find path settings/scuggest_import_path")
      )({
        case JsArray(values) =>
          val stringValues = values.collect { case JsString(value) => value }
          Seq("rt.jar", "scala-library-2.10.6.jar", "/classes", "/test-classes").foreach { entry =>
            trueOrError(stringValues.exists(_.endsWith(entry)), s"scuggest_import_path: could not find $entry")
          }
        case other => sys.error("Expected JsArray but got: ${Json.prettyPrint(other)}")
      })

      (json \ "settings" \ "scuggest_filtered_path").toOption.fold(
        sys.error("could not find path settings/scuggest_filtered_path")
      )({
        case JsArray(values) => trueOrError(values.nonEmpty, "scuggest_filtered_path is empty")
        case other => sys.error("Expected JsArray but got: ${Json.prettyPrint(other)}")
      })
      ()
    }
  )