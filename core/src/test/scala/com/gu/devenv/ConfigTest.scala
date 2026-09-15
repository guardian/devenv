package com.gu.devenv

import io.circe.Json
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for configuration merging and command handling.
  *
  * Decoding is covered by CodecTest, and generated JSON by ConfigJsonTest.
  */
class ConfigTest extends AnyFreeSpec with Matchers with TryValues with HavingMatchers {
  "parseProjectConfig" - {
    "parses a basic config file" in {
      val result = Config
        .parseProjectConfig(
          """name: test
          |""".stripMargin
        )
        .success
        .value
      result shouldBe ProjectConfig("test")
    }

    "parses a simple example config file" in {
      val result = Config
        .parseProjectConfig(
          """name: test
            |modules:
            |  - mise
            |""".stripMargin
        )
        .success
        .value
      result shouldBe ProjectConfig("test", modules = List("mise"))
    }

    "parses a complete project config from YAML" in {
      val exampleConfig =
        scala.io.Source.fromResource("projectConfig.yaml").mkString
      val projectConfig = Config.parseProjectConfig(exampleConfig).success.value

      projectConfig should have(
        "name" as "Scala SBT Development Container",
        "modules" as List("mise"),
        "forwardPorts" as List(
          ForwardPort.SamePort(8080),
          ForwardPort.DifferentPorts(8000, 9000)
        ),
        "remoteEnv" as List(
          Env("SBT_OPTS", "-Xmx2G -XX:+UseG1GC"),
          Env("JAVA_HOME", "/usr/lib/jvm/java-17-openjdk-amd64")
        ),
        "containerEnv" as List(
          Env("EXAMPLE", "foo"),
          Env("EXAMPLE+2", "bar")
        ),
        "mounts" as List(
          Mount.ExplicitMount(
            "${localWorkspaceFolder}/.ivy2",
            "/home/vscode/.ivy2",
            "volume"
          ),
          Mount.ExplicitMount(
            "${localWorkspaceFolder}/.sbt",
            "/home/vscode/.sbt",
            "volume"
          )
        ),
        "postCreateCommand" as List(
          Command("sbt update", "/workspaces/project/subdir", Some("postCreateCommand1")),
          Command("sbt compile", "subdir", Some("postCreateCommand2"))
        ),
        "postStartCommand" as List(
          Command("echo 'Container started successfully'", ".", Some("postStartCommand"))
        ),
        "features" as Map(
          "ghcr.io/devcontainers/features/docker-in-docker:1" -> Json.obj()
        ),
        "updateRemoteUserUID" as true
      )
    }
  }

  "parseUserConfig" - {
    "parses an empty file" in {
      Config.parseUserConfig("").success.value shouldBe UserConfig.empty
    }

    "parses a file containing only comments" in {
      Config
        .parseUserConfig(
          """# A comment
            |# Another comment
            |""".stripMargin
        )
        .success
        .value shouldBe UserConfig.empty
    }

    "parses a complete user config from YAML" in {
      val exampleConfig =
        scala.io.Source.fromResource("userConfig.yaml").mkString
      val userConfig = Config.parseUserConfig(exampleConfig).success.value

      userConfig should have(
        "plugins" as Some(
          Plugins(
            List("com.github.copilot", "com.github.gtache.lsp"),
            List("GitHub.copilot")
          )
        ),
        "dotfiles" as Some(
          Dotfiles(
            "https://github.com/example/dotfiles.git",
            "~",
            "install.sh"
          )
        ),
        "containerSize" as Some(
          ContainerSize.Small
        )
      )
    }
  }

  "mergeConfigs" - {
    "merges user config into project config correctly" in {
      val projectConfig = Config
        .parseProjectConfig(scala.io.Source.fromResource("projectConfig.yaml").mkString)
        .success
        .value
      val userConfig = Config
        .parseUserConfig(scala.io.Source.fromResource("userConfig.yaml").mkString)
        .success
        .value

      val merged = Config.mergeConfigs(projectConfig, Some(userConfig))

      merged.plugins should have(
        "intellij" as List(
          "org.intellij.scala",
          "com.github.gtache.lsp",
          "com.github.copilot"
        ),
        "vscode" as List("scalameta.metals", "scala-lang.scala", "GitHub.copilot")
      )

      merged.onCreateCommand shouldBe projectConfig.onCreateCommand
      merged.postCreateCommand shouldBe List(
        Command("git clone https://github.com/example/dotfiles.git ~", ".", Some("clone")),
        Command("install.sh", "~", Some("dotfiles"))
      ) ++ projectConfig.postCreateCommand

      merged should have(
        "name" as projectConfig.name,
        "forwardPorts" as projectConfig.forwardPorts,
        "remoteEnv" as projectConfig.remoteEnv,
        "containerEnv" as projectConfig.containerEnv,
        "mounts" as projectConfig.mounts,
        "postStartCommand" as projectConfig.postStartCommand,
        "features" as projectConfig.features,
        "updateRemoteUserUID" as projectConfig.updateRemoteUserUID,
        // runArgs gets populated from the merged container size configuration
        // in this case it falls back to the user config, since no project container size is specified
        // the logic for determining how container size properties are rendered to JSON is tested in ConfigJsonTest
        "runArgs" as Config.smallContainerRunArgs
      )
    }

    "preserves project settings (including resolved container size) when user config is absent" in {
      val projectConfig = ProjectConfig(
        "test",
        plugins = Plugins(List("org.intellij.scala"), List("scalameta.metals")),
        postCreateCommand = List(Command("sbt update", ".")),
        containerSize = Some(ContainerSize.Large)
      )

      Config.mergeConfigs(projectConfig, None) shouldBe
        projectConfig.copy(runArgs = Config.largeContainerRunArgs)
    }
  }

  "combineCommands" - {
    "should wrap all commands in brackets before the tee" in {
      val commandMaybe = Config.combineCommands(
        List(Command("ls 1", ".", Some("one")), Command("ls 2", ".", Some("two"))),
        "someLogFile"
      )

      val pattern1 =
        "\\(printf .* Starting one.* && \\(cd . && ls 1 && printf .* Finished one.*\\) \\|\\| printf .* Errored! one.*\\)"
      val pattern2 =
        "\\(printf .* Starting two.* && \\(cd . && ls 2 && printf .* Finished two.*\\) \\|\\| printf .* Errored! two.*\\)"
      val bothPatterns = s"\\($pattern1 && $pattern2\\) 2>&1 \\| sudo tee.*"

      val pattern = bothPatterns.r
      commandMaybe.isDefined shouldBe (true)
      commandMaybe.map(command => pattern.matches(command) shouldBe true)
    }

    "should append trailing commands, even with no configured commands" in {
      val commandMaybe = Config.combineCommands(
        Nil,
        "someLogFile",
        trailingCommands = List(Command.renderCompletionMessage)
      )

      commandMaybe.isDefined shouldBe (true)
      commandMaybe.map(_ should include("Setup complete"))
    }

    "should be empty with no commands and no completion message" in {
      Config.combineCommands(Nil, "someLogFile") shouldBe empty
    }
  }
}
