package com.gu.devenv.modules

import com.gu.devenv.*
import com.gu.devenv.integration.IntegrationTestHelpers.tempDir
import com.gu.devenv.modules.Modules.{Module, ModuleContribution, ResolvedModules}
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
  private val activation  = module.contribution.lifecycleShellSetup.mkString(" && ")
  private val setup       = Command.fromResourceScript("miseLifecycleSetup.sh").success.value
  private val testModule  =
    module.copy(contribution = module.contribution.copy(postCreateCommands = List(setup)))
  private val useTool = Command(
    """printf 'path:%s\n' "$PATH" && devenv-test-tool direct && sh -c 'devenv-test-tool child'""",
    "\"$HOME/project\"",
    Some("useTool")
  )
  private val commands = List(
    useTool,
    Command("false", ".", Some("failure")),
    Command("printf 'after-failure:%s\\n' \"$PWD\"", ".", Some("after"))
  )

  private def hookCommand(json: Json, hook: String): String =
    io.circe.parser.parse(json.spaces2).value.hcursor.get[String](hook).value

  private def runHook(
      command: String,
      home: Path,
      shims: Path,
      miseBinDir: Path,
      path: String = initialPath,
      env: Map[String, String] = Map.empty
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
      "HOME"                     -> home.toString,
      "PATH"                     -> path,
      "DEVENV_TEST_INITIAL_PATH" -> path,
      "DEVENV_TEST_SHIMS_DIR"    -> shims.toString,
      "DEVENV_TEST_MISE_BIN_DIR" -> miseBinDir.toString
    )).foreach { case (key, value) => val _ = environment.put(key, value) }
    val process = builder.start()
    val output  = Using(Source.fromInputStream(process.getInputStream))(_.mkString).success.value
    process.waitFor() shouldBe 0
    output
  }

  "mise lifecycle rendering" - {
    "activates after mise setup on creation and before project commands on start" in {
      val config =
        ProjectConfig("test", postCreateCommand = List(useTool), postStartCommand = List(useTool))
      val json   = Config.configAsJson(config, resolved)
      val create = hookCommand(json, "postCreateCommand")
      val start  = hookCommand(json, "postStartCommand")

      create should startWith("({ printf")
      create.indexOf(module.contribution.postCreateCommands.head.cmd) should be < create.indexOf(
        activation
      )
      create.indexOf(activation) should be < create.indexOf(
        Command.renderCommandWithLogging(useTool)
      )
      start should startWith(s"($activation && ")
      activation should include("mise activate --shims bash")
      activation should not include "export PATH="
    }

    hooks.foreach { hook =>
      s"$hook is unchanged when mise is disabled" in {
        val config =
          ProjectConfig("test", postCreateCommand = List(useTool), postStartCommand = List(useTool))
        val disabled = hookCommand(Config.configAsJson(config, ResolvedModules.empty), hook)
        val trailing =
          if (hook == "postCreateCommand") List(Command.renderCompletionMessage) else Nil
        val logName =
          if (hook == "postCreateCommand") Config.postCreateLogName else Config.postStartLogName

        disabled should not include "mise activate"
        disabled shouldBe Config
          .combineCommands(List(useTool), s"/var/log/$logName", trailing)
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

    "activates before dotfiles and project commands in shared and user configurations" in {
      val config =
        ProjectConfig("test", postCreateCommand = List(useTool), postStartCommand = List(useTool))
      val user = UserConfig(dotfiles =
        Some(Dotfiles("https://example.com/dotfiles", ".", "dotfiles-install"))
      )
      val (userJson, sharedJson) = Config.generateConfigs(config, Some(user), resolved, None)

      List(userJson, sharedJson).foreach { rendered =>
        hooks.foreach { hook =>
          val command = hookCommand(io.circe.parser.parse(rendered).value, hook)
          command.indexOf(activation) should be < command.indexOf(useTool.cmd)
        }
      }
      val userCreate = hookCommand(io.circe.parser.parse(userJson).value, "postCreateCommand")
      userCreate.indexOf(activation) should be < userCreate.indexOf("git clone")
      userCreate.indexOf("dotfiles-install") should be < userCreate.indexOf(useTool.cmd)
    }
  }

  "generated lifecycle commands in a non-interactive shell" - {
    for {
      enabled     <- List(true, false)
      customShims <- List(true, false)
      miseOnPath  <- List(true, false)
    }
      s"mise=$enabled, custom shims=$customShims, mise on PATH=$miseOnPath installs before activating" in
        tempDir.run { dir =>
          val home  = Files.createDirectory(dir.resolve("container home"))
          val work  = Files.createDirectory(home.resolve("project"))
          val shims =
            home.resolve(if (customShims) "custom data/tool shims" else ".local/share/mise/shims")
          val bin       = home.resolve(if (miseOnPath) "system bin" else ".local/bin")
          val path      = if (miseOnPath) s"$bin:$initialPath" else initialPath
          val dependent = Module(
            "dependent",
            "Uses mise tools",
            false,
            ModuleContribution(postCreateCommands =
              List(Command("devenv-test-tool module", ".", Some("dependent")))
            ),
            dependsOn = Set("mise")
          )
          val modules =
            if (enabled)
              Modules.resolveModules(List("dependent", "mise"), List(dependent, testModule)).value
            else ResolvedModules.empty
          val config = ProjectConfig(
            "test",
            postCreateCommand = (if (enabled) Nil else List(setup)) ++ commands,
            postStartCommand = commands
          )
          val json = Config.configAsJson(config, modules)
          Files.exists(shims) shouldBe false
          Files.exists(bin.resolve("mise")) shouldBe false

          // Every invocation starts with the original PATH, as a fresh lifecycle shell would.
          val create = runHook(hookCommand(json, "postCreateCommand"), home, shims, bin, path)
          create should include(s"setup-path:$path")
          create should include("bootstrap-finished")
          create should include("Finished miseLifecycleSetup.sh")
          Files.readString(home.resolve(Config.postCreateLogName)) shouldBe create
          if (enabled) {
            create should include(s"shim:module:$home")
            create.indexOf("bootstrap-finished") should be < create.indexOf("shim:module:")
            create.indexOf("shim:module:") should be < create.indexOf("shim:direct:")
          } else {
            create should not include "shim:module:"
          }

          List(create, runHook(hookCommand(json, "postStartCommand"), home, shims, bin, path))
            .foreach { output =>
              output should include("Errored! failure")
              output should include(s"after-failure:$home")
              output should not include "/child-only"
              if (enabled) {
                output should include(s"path:$shims:$bin:$path")
                output should include(s"shim:direct:$work")
                output should include(s"shim:child:$work")
                output should include("Finished useTool")
              } else {
                output should include(s"path:$path")
                output should include("Errored! useTool")
                output should not include "shim:direct:"
                output should not include "shim:child:"
              }
            }
          val startLog = Files.readString(home.resolve(Config.postStartLogName))
          startLog should not include "bootstrap-finished"
          if (enabled)
            Files.readString(
              home.resolve("activations")
            ) shouldBe "activation-called\nactivation-called\n"
          else
            Files.exists(home.resolve("activations")) shouldBe false
        }

    "does not activate after a failed setup, even if the mise executable was installed" in
      tempDir.run { home =>
        val shims   = home.resolve("shims")
        val bin     = home.resolve(".local/bin")
        val modules = Modules.resolveModules(List("mise"), List(testModule)).value
        val json    = Config.configAsJson(
          ProjectConfig(
            "test",
            postCreateCommand = List(Command("printf 'path:%s\\n' \"$PATH\"", "."))
          ),
          modules
        )
        val output = runHook(
          hookCommand(json, "postCreateCommand"),
          home,
          shims,
          bin,
          env = Map("DEVENV_TEST_SETUP_FAIL" -> "1")
        )

        Files.exists(bin.resolve("mise")) shouldBe true
        Files.exists(home.resolve("activations")) shouldBe false
        output should include("setup-failed")
        output should include("Errored! miseLifecycleSetup.sh")
        output should not include "Finished miseLifecycleSetup.sh"
        output should include(s"path:$initialPath")
      }

    "reports activation failures without evaluating their output in either hook" in
      tempDir.run { home =>
        val shims      = home.resolve("shims")
        val bin        = home.resolve(".local/bin")
        val modules    = Modules.resolveModules(List("mise"), List(testModule)).value
        val reportPath = Command("printf 'path:%s\\n' \"$PATH\"", ".")
        val json       = Config.configAsJson(
          ProjectConfig(
            "test",
            postCreateCommand = List(reportPath),
            postStartCommand = List(reportPath)
          ),
          modules
        )
        val env    = Map("DEVENV_TEST_ACTIVATION_FAIL" -> "1")
        val create = runHook(hookCommand(json, "postCreateCommand"), home, shims, bin, env = env)
        val start  = runHook(hookCommand(json, "postStartCommand"), home, shims, bin, env = env)

        create should include("Errored! miseLifecycleSetup.sh")
        create should not include "Finished miseLifecycleSetup.sh"
        create should include(s"path:$initialPath")
        start should not include "path:"
        List(create, start).foreach { output =>
          output should include("activation-failed")
          output should not include "/must-not-be-evaluated"
        }
        Files.readString(home.resolve(Config.postCreateLogName)) shouldBe create
        Files.readString(home.resolve(Config.postStartLogName)) shouldBe start
      }

    "reports missing mise on start rather than guessing a shims PATH" in
      tempDir.run { home =>
        val config =
          ProjectConfig("test", postStartCommand = List(Command("echo should-not-run", ".")))
        val command = hookCommand(Config.configAsJson(config, resolved), "postStartCommand")
        val output  = runHook(command, home, home.resolve("shims"), home.resolve(".local/bin"))

        output should include(".local/bin/mise")
        output should not include "should-not-run"
        Files.exists(home.resolve("shims")) shouldBe false
        Files.readString(home.resolve(Config.postStartLogName)) shouldBe output
      }
  }
}
