package com.gu.devenv

import com.gu.devenv.ContainerSize.Small
import io.circe.Json
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{OptionValues, TryValues}

import scala.util.Success

/** Unit tests for Config object functions (parsing and merging).
  *
  * Note: JSON generation is tested separately in ProjectConfigJsonTest using property-based tests.
  * End-to-end integration with file system operations is tested in integration/ package.
  */
class ConfigTest
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with OptionValues
    with HavingMatchers {

  "parseProjectConfig" - {
    "parses a complete project config from YAML" in {
      val exampleConfig =
        scala.io.Source.fromResource("projectConfig.yaml").mkString
      val Success(projectConfig) =
        Config.parseProjectConfig(exampleConfig).success

      projectConfig should have(
        "name" as "Scala SBT Development Container",
        "image" as "mcr.microsoft.com/devcontainers/base:ubuntu",
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
          Command("sbt update", "/workspaces/project/subdir"),
          Command("sbt compile", "subdir")
        ),
        "postStartCommand" as List(
          Command("echo 'Container started successfully'", ".")
        ),
        "features" as Map(
          "ghcr.io/devcontainers/features/docker-in-docker:1" -> Json.obj()
        ),
        "remoteUser" as "vscode",
        "updateRemoteUserUID" as true
      )
    }
  }

  "parseUserConfig" - {
    "parses a complete user config from YAML" in {
      val exampleConfig =
        scala.io.Source.fromResource("userConfig.yaml").mkString
      val Success(userConfig) =
        Config.parseUserConfig(exampleConfig).success

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
          Small
        )
      )
    }

    "parses an empty user config file" in {
      val emptyConfig = ""
      val Success(userConfig) =
        Config.parseUserConfig(emptyConfig).success
      userConfig shouldBe UserConfig.empty
    }

    "parses a user config file with only comments" in {
      val commentsOnlyConfig = """# This is a comment
                                 |# Another comment
                                 |""".stripMargin
      val Success(userConfig) =
        Config.parseUserConfig(commentsOnlyConfig).success

      userConfig shouldBe UserConfig.empty
    }
  }

  "mergeConfigs" - {
    "merges user config into project config correctly" in {
      val projectConfigYaml =
        scala.io.Source.fromResource("projectConfig.yaml").mkString
      val userConfigYaml =
        scala.io.Source.fromResource("userConfig.yaml").mkString

      val Success(projectConfig) =
        Config.parseProjectConfig(projectConfigYaml).success
      val Success(userConfig) =
        Config.parseUserConfig(userConfigYaml).success

      val merged = Config.mergeConfigs(projectConfig, Some(userConfig))

      // Plugins should be merged and deduplicated (project plugins + user plugins)
      merged.plugins should have(
        "intellij" as List(
          "org.intellij.scala",
          "com.github.gtache.lsp",
          "com.github.copilot"
        ),
        "vscode" as List("scalameta.metals", "scala-lang.scala", "GitHub.copilot")
      )

      // Dotfiles commands should be prepended to postCreateCommand
      merged.postCreateCommand should have length 4
      merged.postCreateCommand.take(2) shouldBe List(
        Command("git clone https://github.com/example/dotfiles.git ~", "."),
        Command("install.sh", "~")
      )
      merged.postCreateCommand.drop(2) shouldBe projectConfig.postCreateCommand

      // Other fields should remain unchanged from project config
      merged should have(
        "name" as projectConfig.name,
        "image" as projectConfig.image,
        "forwardPorts" as projectConfig.forwardPorts,
        "remoteEnv" as projectConfig.remoteEnv,
        "containerEnv" as projectConfig.containerEnv,
        "mounts" as projectConfig.mounts,
        "postStartCommand" as projectConfig.postStartCommand,
        "features" as projectConfig.features,
        "remoteUser" as projectConfig.remoteUser,
        "updateRemoteUserUID" as projectConfig.updateRemoteUserUID,
        // Note this is a list inferred from the "small" container size configuration item in the yaml
        "runArgs" as Config.smallContainerRunArgs
      )
    }

    "merges user config with large container into project config correctly" in {
      Config.mergeConfigs(
        ProjectConfig("test"),
        Some(UserConfig(containerSize = Some(ContainerSize.Large)))
      ) should have(
        "runArgs" as Config.largeContainerRunArgs
      )
    }

    "merges user config with defaulted container into project config correctly" in {
      Config.mergeConfigs(
        ProjectConfig("test"),
        Some(UserConfig(containerSize = None))
      ) should have(
        "runArgs" as Config.largeContainerRunArgs
      )
    }

    "returns unchanged project config when user config is None" in {
      val projectConfigYaml =
        scala.io.Source.fromResource("projectConfig.yaml").mkString
      val Success(projectConfig) =
        Config.parseProjectConfig(projectConfigYaml).success

      val merged = Config.mergeConfigs(projectConfig, None)

      merged shouldBe projectConfig
    }
  }
}
