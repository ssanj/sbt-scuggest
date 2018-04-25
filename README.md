# SBT Scuggest

SBT Scuggest is an SBT plugin that will generate [Scuggest](https://github.com/ssanj/Scuggest) entries for your Sublime Text project. It does this by scanning through your SBT build and generating entries for your dependencies. It then adds these dependencies into your sublime project file if one exists or creates one if it doesn't. It creates a backup of the original project file, so nothing is lost if anything goes awry.

_NB_: In addition if SBT Scuggest finds a __JAVA_HOME__ environment variable defined, it automatically includes the __rt.jar__ from your JDK or JRE. If this is not what you want you can exclude it through `scuggestDepFilters`.

## Configuration

If you're using SBT versions 0.13.0 to 0.13.9 add the following to your __project/plugins.sbt__:

```scala
addSbtPlugin("net.ssanj" % "sbt-scuggest" % "0.0.4.2")
```

For SBT version 0.13.10 and 1.x add the following to your __project/plugins.sbt__:

```scala
addSbtPlugin("net.ssanj" % "sbt-scuggest" % "0.0.7.0")
```

To install it globally add the required version to __~/.sbt/0.13/plugins/plugin.sbt__ or __~/.sbt/1.0/plugins/plugin.sbt__ depending on your sbt version.

If the plugin fails to resolve add this resolver to your __project/plugins.sbt__:

```scala
resolvers += Resolver.url("ssanj", new URL("https://dl.bintray.com/ssanj/sbt-plugins"))(Resolver.ivyStylePatterns)
```

## Settings

* `scuggestSimulate`: Whether to simulate updating the Sublime project file (write to STDOUT) or to update the project file. Defaults to `false`.

Run `set scuggestSimulate := true` and run `scuggestGen` to see what will be added to your Sublime project file.

* `scuggestSublimeProjName`: The name of the sublime project file (*.sublime-project). The default is to use the SBT build name.

* `scuggestClassDirs`: Which directories to look at for loading main and test classes. Defaults to those specified in your SBT build. (`target/scala_2.xx/classes` and `target/scala_2.xx/test-classes`)

* `scuggestSearchFilters`: Which paths to ignore when searching for classes. Defaults to `List("sun", "com/sun")`.

* `scuggestDepFilters`: Any dependencies to not include in the project file. Defaults to: `List(test-interface-.+\.jar$, scala-parser-combinators_.+\.jar$, scala-reflect-.+\.jar$, scala-compiler-.+\.jar$, jline-.+\.jar$, scala-xml_.+\.jar)`

* `scuggestVerbose`: Additional logging about how dependencies are calculated. Defaults to `false`.

## Tasks

* `scuggestGen`: Outputs an updated Sublime project file to STDOUT if `scuggestSimulate` is `true` - which is the default, or updates your Sublime project file (or creates one if it does not exist) if `scuggestSimulate` is `false`.

```scala
[info] ----- This is a simulation -----
[info] /Volumes/Work/projects/code/scala/toy/TestScuggestSbtPlugin/test-scuggest.sublime-project will be updated the following contents:
[info] {
[info]   "folders" : [ {
[info]     "path" : "."
[info]   } ],
[info]   "settings" : {
[info]     "Scoggle" : {
[info]       "test_suffixes" : [ "Spec.scala", "Props.scala" ]
[info]     },
[info]     "scuggest_import_path" : [ "/Users/sanj/.ivy2/cache/org.scala-lang/scala-reflect/jars/scala-reflect-2.11.2.jar", "/Users/sanj/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.11.7.jar", "/Users/sanj/.ivy2/cache/org.scalaz/scalaz-core_2.11/jars/scalaz-core_2.11-7.1.4.jar", "/Users/sanj/.ivy2/cache/org.scalacheck/scalacheck_2.11/jars/scalacheck_2.11-1.12.5.jar", "/Users/sanj/.ivy2/cache/org.scala-lang.modules/scala-xml_2.11/jars/scala-xml_2.11-1.0.4.jar", "/Users/sanj/.ivy2/cache/org.scalatest/scalatest_2.11/jars/scalatest_2.11-2.2.4.jar", "/Volumes/Work/projects/code/scala/toy/TestScuggestSbtPlugin/target/scala-2.11/classes", "/Volumes/Work/projects/code/scala/toy/TestScuggestSbtPlugin/target/scala-2.11/test-classes", "/Library/Java/JavaVirtualMachines/jdk1.8.0_65.jdk/Contents/Home/jre/lib/rt.jar" ],
[info]     "scuggest_filtered_path" : [ "sun", "com/sun" ]
[info]   }
[info] }
[info] To update the project with the above contents, "set scuggestSimulate := false" and run scuggestGen.
```

## Tests

```
^ scripted
```

## Publishing

To publish a new version perform the following tasks:

1. `^ publish`
2. `bintrayRelease`

## Caveats

Support for multimodule projects is untested.