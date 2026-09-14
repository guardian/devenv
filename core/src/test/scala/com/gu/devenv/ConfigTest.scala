package com.gu.devenv

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues

/** Unit tests for configuration merging and command handling.
  *
  * Decoding is covered by CodecTest, and generated JSON by ConfigJsonTest.
  */
class ConfigTest extends AnyFreeSpec with Matchers with TryValues with HavingMatchers {
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
        "updateRemoteUserUID" as projectConfig.updateRemoteUserUID
      )
    }

    "preserves project settings when user config is absent" in {
      val projectConfig = ProjectConfig(
        "test",
        plugins = Plugins(List("org.intellij.scala"), List("scalameta.metals")),
        postCreateCommand = List(Command("sbt update", "."))
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
