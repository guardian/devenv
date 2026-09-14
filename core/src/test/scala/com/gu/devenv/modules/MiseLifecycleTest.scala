package com.gu.devenv.modules

import com.gu.devenv.*
import com.gu.devenv.integration.IntegrationTestHelpers.tempDir
import com.gu.devenv.modules.Modules.ResolvedModules
import io.circe.Json
import org.scalatest.{EitherValues, OptionValues, TryValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import scala.io.Source
import scala.util.Using

class MiseLifecycleTest
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with EitherValues
    with OptionValues {
  private val initialPath = "/usr/bin:/bin"
  private val hooks       = List("postCreateCommand", "postStartCommand")
  private val module      = mise.success.value
  private val resolved    = Modules.resolveModules(List("mise"), List(module)).value
  private val exportPath  =
    """export PATH="${MISE_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/mise}/shims:$PATH""""

  private def hookCommand(json: Json, hook: String): String =
    io.circe.parser.parse(json.spaces2).value.hcursor.get[String](hook).value

  private def runHook(
      command: String,
      home: Path,
      shims: Path,
      env: Map[String, String]
  ): String = {
    // Keep the generated pipeline intact, but redirect privileged log writes into the test home.
    val shell =
      """sudo() { [ "$1" = tee ] && shift && tee "$HOME/${1##*/}"; }; """ + command
    val builder = new ProcessBuilder("/bin/sh", "-c", shell)
      .directory(home.toFile)
      .redirectErrorStream(true)
    val environment = builder.environment()
    environment.clear()
    (env ++ Map(
      "HOME"                  -> home.toString,
      "PATH"                  -> initialPath,
      "DEVENV_TEST_SHIMS_DIR" -> shims.toString
    )).foreach { case (key, value) => val _ = environment.put(key, value) }
    val process = builder.start()
    val output  = Using(Source.fromInputStream(process.getInputStream))(_.mkString).success.value
    process.waitFor() shouldBe 0
    output
  }

  "mise lifecycle rendering" - {
    hooks.foreach { hook =>
      s"$hook exports shims before every command only when mise is enabled" in {
        val command = Command("npm install", "/workspaces/project", Some("install"))
        val config  = ProjectConfig(
          name = "test",
          postCreateCommand = List(command),
          postStartCommand = List(command)
        )
        val enabled  = hookCommand(Config.configAsJson(config, resolved), hook)
        val disabled = hookCommand(Config.configAsJson(config, ResolvedModules.empty), hook)

        enabled should startWith(s"$exportPath && (")
        enabled should include(Command.renderCommandWithLogging(command))
        disabled should not include "export PATH="
        val trailing =
          if (hook == "postCreateCommand") List(Command.renderCompletionMessage) else Nil
        val logName =
          if (hook == "postCreateCommand") Config.postCreateLogName else Config.postStartLogName
        disabled shouldBe Config
          .combineCommands(List(command), s"/var/log/$logName", trailing)
          .value
      }
    }

    "does not change onCreateCommand or add an empty postStartCommand" in {
      val config   = ProjectConfig("test", onCreateCommand = List(Command("echo unchanged", ".")))
      val enabled  = Config.configAsJson(config, resolved)
      val disabled = Config.configAsJson(config, ResolvedModules.empty)

      enabled.hcursor.get[String]("onCreateCommand") shouldBe
        disabled.hcursor.get[String]("onCreateCommand")
      enabled.hcursor.downField("postStartCommand").succeeded shouldBe false
      disabled.hcursor.downField("postStartCommand").succeeded shouldBe false
    }

    "exports shims in both shared and user configurations" in {
      val config = ProjectConfig("test", postStartCommand = List(Command("echo started", ".")))
      val user = UserConfig(dotfiles = Some(Dotfiles("https://example.com/dotfiles", ".", "true")))
      val (userJson, sharedJson) = Config.generateConfigs(config, Some(user), resolved, None)

      List(userJson, sharedJson).foreach { rendered =>
        hooks.foreach { hook =>
          hookCommand(io.circe.parser.parse(rendered).value, hook) should startWith(exportPath)
        }
      }
    }
  }

  "generated lifecycle commands in a non-interactive shell" - {
    for {
      hook     <- hooks
      enabled  <- List(true, false)
      location <- List("default", "empty overrides", "XDG_DATA_HOME", "MISE_DATA_DIR")
    }
      s"$hook with mise=$enabled and $location preserves PATH across child bootstrap" in
        tempDir.run { dir =>
          val home         = Files.createDirectory(dir.resolve("container home"))
          val work         = Files.createDirectory(home.resolve("project"))
          val (env, shims) = location match {
            case "XDG_DATA_HOME" =>
              (
                Map("XDG_DATA_HOME" -> home.resolve("xdg data").toString),
                home.resolve("xdg data/mise/shims")
              )
            case "MISE_DATA_DIR" =>
              (
                Map(
                  "MISE_DATA_DIR" -> home.resolve("mise data").toString,
                  "XDG_DATA_HOME" -> home.resolve("unused xdg data").toString
                ),
                home.resolve("mise data/shims")
              )
            case "empty overrides" =>
              (
                Map("MISE_DATA_DIR" -> "", "XDG_DATA_HOME" -> ""),
                home.resolve(".local/share/mise/shims")
              )
            case _ => (Map.empty[String, String], home.resolve(".local/share/mise/shims"))
          }
          val setup   = Command.fromResourceScript("miseLifecycleSetup.sh").success.value
          val useTool = Command(
            """printf 'path:%s\n' "$PATH" && devenv-test-tool direct && sh -c 'devenv-test-tool child'""",
            "\"$HOME/project\"",
            Some("useTool")
          )
          val afterFailure = Command("printf 'after-failure:%s\\n' \"$PWD\"", ".", Some("after"))
          val commands     = List(useTool, Command("false", ".", Some("failure")), afterFailure)
          val testModule   = module.copy(contribution =
            module.contribution.copy(
              postCreateCommands = List(setup)
            )
          )
          val modules =
            if (enabled) Modules.resolveModules(List("mise"), List(testModule)).value
            else ResolvedModules.empty
          val config = ProjectConfig(
            "test",
            postCreateCommand = (if (enabled) Nil else List(setup)) ++ commands,
            postStartCommand = setup :: commands
          )
          val rendered = hookCommand(Config.configAsJson(config, modules), hook)
          Files.exists(shims) shouldBe false
          val output = runHook(rendered, home, shims, env)

          output should include("bootstrap-finished")
          output should include("Finished miseLifecycleSetup.sh")
          output should include("Errored! failure")
          output should include(s"after-failure:$home")
          output should not include "/child-only"
          if (enabled) {
            output should include(s"path:$shims:$initialPath")
            output should include(s"shim:direct:$work")
            output should include(s"shim:child:$work")
            output should include("Finished useTool")
            output.indexOf("bootstrap-finished") should be < output.indexOf("shim:direct:")
          } else {
            output should include(s"path:$initialPath")
            output should include("Errored! useTool")
            output should not include "shim:direct:"
            output should not include "shim:child:"
          }
          val logName =
            if (hook == "postCreateCommand") Config.postCreateLogName else Config.postStartLogName
          Files.readString(home.resolve(logName)) shouldBe output
        }
  }
}
