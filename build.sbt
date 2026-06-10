ThisBuild / scalaVersion := "3.3.7" // latest LTS
ThisBuild / organization := "com.gu"
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "utf8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Xfatal-warnings"
)

// Fast startup JVM options for short-lived CLI processes
val cliJvmOptions = Seq(
  "-XX:+TieredCompilation",  // Enable tiered compilation
  "-XX:TieredStopAtLevel=1", // Stop at C1 compiler (faster startup, less optimization)
  "-Xshare:auto",            // Use class data sharing if available
  "-XX:+UseSerialGC",        // Faster GC for short-lived processes
  "-Xms64m",                 // Small initial heap
  "-Xmx512m"                 // Reasonable max heap
)

// shared library versions
val circeVersion         = "0.14.15"
val sttpVersion          = "4.0.25"
val scalatestVersion     = "3.2.20"
val scalaCheckVersion    = "1.19.0"
val scalatestPlusVersion = "3.2.20.0"

// empty root project to aggregate all subprojects
lazy val root = (project in file("."))
  .settings(
    name := "devenv"
  )
  .aggregate(cli, core, docker)

// the packaged CLI application
lazy val cli = (project in file("cli"))
  .enablePlugins(JavaAppPackaging, GraalVMNativeImagePlugin)
  .settings(
    name    := "devenv",
    version := "0.1.0",
    libraryDependencies ++= Seq(
      "com.lihaoyi"                   %% "fansi"           % "0.5.1",
      "com.softwaremill.sttp.client4" %% "core"            % sttpVersion,
      "com.softwaremill.sttp.client4" %% "circe"           % sttpVersion,
      "org.scalatest"                 %% "scalatest"       % scalatestVersion     % Test,
      "org.scalacheck"                %% "scalacheck"      % scalaCheckVersion    % Test,
      "org.scalatestplus"             %% "scalacheck-1-19" % scalatestPlusVersion % Test
    ),
    Compile / mainClass  := Some("com.gu.devenv.Main"),
    executableScriptName := "devenv",
    // Apply CLI JVM options to packaged binary
    bashScriptExtraDefines ++= cliJvmOptions.map(opt => s"""addJava "$opt""""),

    // GraalVM Native Image configuration
    graalVMNativeImageOptions ++= {
      // Use compatibility mode for Linux builds to support older CPUs and containers
      // macOS builds use native optimizations for best performance
      val marchOption = sys.env.get("DEVENV_ARCHITECTURE") match {
        case Some(arch) if arch.startsWith("linux") => Seq("-march=compatibility")
        case _                                      => Seq.empty
      }

      Seq(
        "--no-fallback",                     // Fail if native image cannot be built
        "--initialize-at-build-time",        // Initialize most classes at build time
        "--enable-url-protocols=http,https", // Enable HTTP/HTTPS
        "-H:+ReportExceptionStackTraces",    // Better error reporting during build
        "-EDEVENV_RELEASE",      // Bake the CI build version environment variable into the binary
        "-EDEVENV_ARCHITECTURE", // Bake the architecture environment variable into the binary
        "-EDEVENV_BRANCH",       // Bake the branch name environment variable into the binary
        "--verbose",             // Show build progress
        // Optimization flags
        "-O2",        // Optimize for performance
        "--gc=serial" // Use serial GC (suitable for CLI tools)
      ) ++ marchOption
    },
    // Output binary name
    GraalVMNativeImage / name := "devenv"
  )
  .dependsOn(core)

lazy val core = project
  .in(file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "io.circe"          %% "circe-core"           % circeVersion,
      "io.circe"          %% "circe-generic"        % circeVersion,
      "io.circe"          %% "circe-parser"         % circeVersion,
      "io.circe"          %% "circe-generic-extras" % "0.14.5-RC1",
      "io.circe"          %% "circe-yaml-scalayaml" % "0.16.1",
      "com.lihaoyi"       %% "fansi"                % "0.5.1",
      "org.typelevel"     %% "cats-core"            % "2.13.0",
      "org.scalatest"     %% "scalatest"            % scalatestVersion     % Test,
      "org.scalacheck"    %% "scalacheck"           % scalaCheckVersion    % Test,
      "org.scalatestplus" %% "scalacheck-1-19"      % scalatestPlusVersion % Test
    )
  )

lazy val docker = project
  .in(file("docker"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    ),
    // include test duration in feedback for each docker test
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
  )
  .dependsOn(core)
