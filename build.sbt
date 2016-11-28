import java.nio.file._

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishMavenStyle := true,
  publishArtifact := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  licenses := Seq(
    "MIT" -> url("http://www.opensource.org/licenses/mit-license.php")),
  homepage := Some(url("https://github.com/olafurpg/db-codegen")),
  autoAPIMappings := true,
  apiURL := Some(url("https://github.com/olafurpg/db-codegen")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/olafurpg/db-codegen"),
      "scm:git:git@github.com:olafurpg/db-codegen.git"
    )
  ),
  pomExtra :=
    <developers>
        <developer>
          <id>olafurpg</id>
          <name>Ólafur Páll Geirsson</name>
          <url>https://geirsson.com</url>
        </developer>
      </developers>
)

val deployToBin = taskKey[Unit]("deploy-bin")
val deployBinPath = settingKey[String]("deploy binary path")
val circeVersion = "0.6.0"

lazy val `launaskil-codegen` =
  (project in file("."))
//    .enablePlugins(assembly)
    .settings(packSettings)
    .settings(publishSettings)
    .settings(
      name := "scala-db-codegen",
      organization := "com.geirsson",
      scalaVersion := "2.11.8",
      version := com.geirsson.codegen.Versions.nightly,
      packMain := Map("scala-db-codegen" -> "com.geirsson.codegen.Codegen"),
      mainClass in assembly := Some("com.geirsson.codegen.Codegen"),
      assemblyJarName in assembly := "db-codegen.jar",
      deployBinPath := sbt.Path.userHome.absolutePath + "/DevOps/bin/",
      deployToBin := {
        assembly.value
        Files.copy(
          Paths.get(target.value + "/scala-2.11/" + (assemblyJarName in assembly).value),
//          Paths.get("/Users/apollo/IdeaProjects/Projects/Work/so/scala-db-codegen/target/scala-2.11/db-codegen.jar"),
          Paths.get(deployBinPath.value + "db-codegen.jar"),
          StandardCopyOption.REPLACE_EXISTING
        )
      },
//      assemblyOutputPath := new java.io.File("/Users/apollo/DevOps/bin/" + (assemblyJarName in assembly).value),

      /*
      deployToBin := {
        assembly.value

        println("assembly output path is")
        println(assemblyOutputPath.value)
        println("assembly output path is")

        assemblyOutputPath.map { file =>
          println("copying files : " + file.toPath + " " + file.name)
          Files.copy(file.toPath, new File(deployBinPath.value, file.name).toPath)  
        }
        */



      Keys.fork in Test := false,
      Keys.parallelExecution in Test := false,
      libraryDependencies ++= Seq(
        "com.geirsson" %% "scalafmt-core" % "0.3.0",
        "io.getquill" %% "quill-core" % "1.0.1-SNAPSHOT",
        "io.getquill" %% "quill-jdbc" % "1.0.1-SNAPSHOT",
        "com.zaxxer" % "HikariCP" % "2.4.7",
        "com.h2database" % "h2" % "1.4.192",
        "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
        "com.github.alexarchambault" %% "case-app" % "1.0.0-RC3",
        "org.scalatest" %% "scalatest" % "3.0.0" % "test",
        "ch.qos.logback" % "logback-classic" % "1.1.7",
        "com.github.nscala-time" %% "nscala-time" % "2.14.0",
         "org.json4s" %% "json4s-native" % "3.5.0"
//        "io.circe" %% "circe-core" % circeVersion,
//        "io.circe" %% "circe-generic" % circeVersion,
//        "io.circe" %% "circe-parser" % circeVersion
      )
    )
