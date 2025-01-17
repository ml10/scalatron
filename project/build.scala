import sbt._
import Keys._
import sbtassembly.Plugin._
import AssemblyKeys._


object build extends Build {
  //sbt.Keys.fork in ( Test, run ) := true

  def standardSettings = Defaults.defaultSettings ++ src ++ assemblySettings ++ Seq(
    mergeStrategy in assembly <<= (mergeStrategy in assembly) {
      (old) => {
        case PathList("javax", "ws", "rs", xs@_*) => MergeStrategy.first
        case "META-INF/ECLIPSE_.RSA" => MergeStrategy.first
        case "plugin.properties" => MergeStrategy.first
        case "about.html" => MergeStrategy.first
        case x => old(x)
      }
    }
  )

  lazy val implVersion = Seq(
    packageOptions <<= (version) map {
      scalatronVersion => Seq(Package.ManifestAttributes(
        ("Implementation-Version", scalatronVersion)
      ))
    }
  )

  lazy val all = Project(
    id = "all",
    base = file("."),
    settings = standardSettings ++ Seq(distTask),
    aggregate = Seq(main, cli, markdown, referenceBot, tagTeamBot)
  )

  lazy val src = Seq(
    scalaSource in Compile <<= baseDirectory / "src",
    scalaSource in Test <<= baseDirectory / "test"
  )

  lazy val core = Project("ScalatronCore", file("ScalatronCore"),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.5"
      )
    ) ++ Seq(
      jarName in assembly := "ScalatronCore.jar" // , logLevel in assembly := Level.Debug
    )
  )

  lazy val botwar = Project("BotWar", file("BotWar"),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.5"
      )
    ) ++ Seq(
      jarName in assembly := "BotWar.jar" // , logLevel in assembly := Level.Debug
    )
  ) dependsOn (core)

  lazy val main = Project("Scalatron", file("Scalatron"),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % "2.11.2",
        "com.typesafe.akka" %% "akka-actor" % "2.3.5",
        "org.eclipse.jetty.aggregate" % "jetty-webapp" % "8.1.15.v20140411" intransitive(),
        "com.fasterxml.jackson.core" % "jackson-core" % "2.4.1",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.1",
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.1",
//        "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-providers" % "2.4.1",
        "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % "2.4.1",
        //        "org.codehaus.jackson" % "jackson-jaxrs" % "1.9.13",
        "com.sun.jersey" % "jersey-bundle" % "1.18.1",
        "javax.servlet" % "javax.servlet-api" % "3.0.1",
        "org.eclipse.jgit" % "org.eclipse.jgit.http.server" % "3.4.1.201406201815-r",
        "com.sksamuel.elastic4s" %% "elastic4s" % "1.2.1.2",
        "org.scalatest" %% "scalatest" % "2.2.2" % "test",
        "org.testng" % "testng" % "6.8.8" % "test",
        "org.specs2" %% "specs2" % "2.4.1" % "test"
        //        ,
        //        "org.specs2" %% "specs2-scalaz-core" % "7.0.0"
      ),
      resolvers += "JGit Repository" at "http://download.eclipse.org/jgit/maven"
    ) ++ Seq(
      jarName in assembly := "Scalatron.jar" // , logLevel in assembly := Level.Debug
    )
  ) dependsOn (botwar)

  lazy val cli = Project("ScalatronCLI", file("ScalatronCLI"),
    settings = standardSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.apache.httpcomponents" % "httpclient" % "4.3.5",
        "org.scala-lang" % "scala-parser-combinators" % "2.11.0-M4"
      )
    ) ++ Seq(
      jarName in assembly := "ScalatronCLI.jar"
    )
  )

  lazy val markdown = Project("ScalaMarkdown", file("ScalaMarkdown"),
    settings = standardSettings ++ Seq(
      scalaSource in Compile <<= baseDirectory / "src",
      scalaSource in Test <<= baseDirectory / "test/scala",
      resourceDirectory in Test <<= baseDirectory / "test/resources"
    ) ++ Seq(
      libraryDependencies ++= Seq(
        "org.specs2" %% "specs2" % "2.4.1" % "test",
        "commons-io" % "commons-io" % "2.4",
        "org.apache.commons" % "commons-lang3" % "3.3.2"
      )
    ) ++ Seq(
      jarName in assembly := "ScalaMarkdown.jar"
    )
  )

  lazy val samples: Map[String, Project] = (IO.listFiles(file("Scalatron") / "samples")) filter (!_.isFile) map {
    sample: File => sample.getName -> Project(sample.getName, sample, settings = Defaults.defaultSettings ++ Seq(
      scalaSource in Compile <<= baseDirectory / "src",
      artifactName in packageBin := ((_, _, _) => "ScalatronBot.jar")
    ))
  } toMap

  // TODO How can we do this automatically?!?
  lazy val referenceBot = samples("ExampleBot01-Reference")
  lazy val tagTeamBot = samples("ExampleBot02-TagTeam")

  val dist = TaskKey[Unit]("dist", "Makes the distribution zip file")
  val distTask = dist <<= (version, scalaBinaryVersion) map {
    (scalatronVersion, version) =>
      println("Beginning distribution generation...")
      val distDir = file("dist")

      // clean distribution directory
      println("Deleting /dist directory...")
      IO delete distDir

      // create new distribution directory
      println("Creating /dist directory...")
      IO createDirectory distDir
      val scalatronDir = file("Scalatron")

      println("Copying Readme.txt and License.txt...")
      for (fileToCopy <- List("Readme.txt", "License.txt")) {
        IO.copyFile(scalatronDir / fileToCopy, distDir / fileToCopy)
      }

      for (dirToCopy <- List("webui", "doc/pdf")) {
        println("Copying " + dirToCopy)
        IO.copyDirectory(scalatronDir / dirToCopy, distDir / dirToCopy)
      }

      val distSamples = distDir / "samples"
      def sampleJar(sample: Project) = sample.base / ("target/scala-%s/ScalatronBot.jar" format version)
      for (sample <- samples.values) {
        if (sampleJar(sample).exists) {
          println("Copying " + sample.base)
          IO.copyDirectory(sample.base / "src", distSamples / sample.base.getName / "src")
          IO.copyFile(sampleJar(sample), distSamples / sample.base.getName / "ScalatronBot.jar")
        }
      }

      println("Copying Reference bot to /bots directory...")
      IO.copyFile(sampleJar(referenceBot), distDir / "bots" / "Reference" / "ScalatronBot.jar")


      def markdown(docDir: File, htmlDir: File) = {
        Seq("java", "-Xmx1G", "-jar", "ScalaMarkdown/target/scala-2.11/ScalaMarkdown.jar", docDir.getPath, htmlDir.getPath) !
      }

      // generate HTML from Markdown, for /doc and /devdoc
      println("Generating /dist/doc/html from /doc/markdown...")
      markdown(scalatronDir / "doc/markdown", distDir / "doc/html")

      println("Generating /webui/tutorial from /dev/tutorial...")
      markdown(scalatronDir / "doc/tutorial", distDir / "webui/tutorial")



      for (jar <- List("Scalatron", "ScalatronCLI", "ScalatronCore", "BotWar")) {
        IO.copyFile(file(jar) / "target/scala-2.11" / (jar + ".jar"), distDir / "bin" / (jar + ".jar"))
      }

      // This is ridiculous, there has to be be an easier way to zip up a directory
      val zipFileName = "scalatron-%s.zip" format scalatronVersion
      println("Zipping up /dist into " + zipFileName + "...")
      def zip(srcDir: File, destFile: File, prepend: String) = {
        val allDistFiles = (srcDir ** "*").get.filter(_.isFile).map {
          f => (f, prepend + IO.relativize(distDir, f).get)
        }
        IO.zip(allDistFiles, destFile)
      }
      zip(distDir, file("./" + zipFileName), "Scalatron/")
  } dependsOn(
    assembly in core,
    assembly in botwar,
    assembly in main,
    assembly in cli,
    assembly in markdown,
    packageBin in Compile in referenceBot,
    packageBin in Compile in tagTeamBot)
}