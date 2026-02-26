package com.gu.devenv

import io.circe.Json
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Property-based tests for Config.configAsJson function.
  *
  * Tests that ProjectConfig fields correctly map to the devcontainer.json structure using
  * ScalaCheck generators and Circe cursor navigation.
  *
  * Note: Config parsing/merging is tested in ConfigTest with real fixtures.
  */
class ConfigJsonTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {
  "Config.configAsJson" - {
    "base fields" - {
      "name appears in JSON name field" in {
        forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { name =>
          val config = ProjectConfig(name = name)
          val json   = Config.configAsJson(config, Nil).get

          json.hcursor.downField("name").as[String] shouldBe Right(name)
        }
      }

      "image appears in JSON image field" in {
        forAll(Gen.alphaNumStr.suchThat(_.nonEmpty)) { image =>
          val config = ProjectConfig(name = "test", image = image)
          val json   = Config.configAsJson(config, Nil).get

          json.hcursor.downField("image").as[String] shouldBe Right(image)
        }
      }
    }

    "features field" - {
      val genFeatures: Gen[Map[String, Json]] = for {
        size <- Gen.choose(1, 5)
        keys <- Gen.listOfN(size, Gen.alphaNumStr.suchThat(_.nonEmpty)).map(_.distinct)
        values <- Gen.listOfN(
          keys.size,
          Gen.oneOf(
            Gen.const(Json.obj()),
            Gen.alphaNumStr.map(Json.fromString),
            Gen.choose(0, 100).map(Json.fromInt),
            Gen.const(Json.True),
            Gen.const(Json.False)
          )
        )
      } yield keys.zip(values).toMap

      "appears in JSON features object" in {
        forAll(genFeatures) { features =>
          val config = ProjectConfig(name = "test", features = features)
          val json   = Config.configAsJson(config, Nil).get

          val featuresJson = json.hcursor.downField("features").as[Json]
          featuresJson shouldBe a[Right[_, _]]

          // Check that each feature is present in the JSON
          features.foreach { case (key, value) =>
            json.hcursor.downField("features").downField(key).as[Json] shouldBe Right(value)
          }
        }
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", features = Map.empty)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("features").as[Json] shouldBe a[Left[_, _]]
      }
    }

    "mounts field" - {
      val genMount: Gen[Mount] = Gen.oneOf(
        Gen.alphaNumStr.suchThat(_.nonEmpty).map(Mount.ShortMount(_)),
        for {
          source    <- Gen.alphaNumStr.suchThat(_.nonEmpty)
          target    <- Gen.alphaNumStr.suchThat(_.nonEmpty)
          mountType <- Gen.oneOf("volume", "bind", "tmpfs")
        } yield Mount.ExplicitMount(source, target, mountType)
      )

      val genMounts: Gen[List[Mount]] = Gen.nonEmptyListOf(genMount)

      "appears in JSON mounts array" in {
        forAll(genMounts) { mounts =>
          val config = ProjectConfig(name = "test", mounts = mounts)
          val json   = Config.configAsJson(config, Nil).get

          val mountsJson = json.hcursor.downField("mounts").as[List[Json]]
          mountsJson shouldBe a[Right[_, _]]
          mountsJson.map(_.size) shouldBe Right(mounts.size)
        }
      }

      "short mount appears as string in JSON" in {
        val mount  = Mount.ShortMount("source:/target")
        val config = ProjectConfig(name = "test", mounts = List(mount))
        val json   = Config.configAsJson(config, Nil).get

        val mountsJson = json.hcursor.downField("mounts").downN(0).as[String]
        mountsJson shouldBe Right("source:/target")
      }

      "explicit mount appears as object in JSON" in {
        val mount  = Mount.ExplicitMount("mysource", "mytarget", "bind")
        val config = ProjectConfig(name = "test", mounts = List(mount))
        val json   = Config.configAsJson(config, Nil).get

        val mountJson = json.hcursor.downField("mounts").downN(0)
        mountJson.downField("source").as[String] shouldBe Right("mysource")
        mountJson.downField("target").as[String] shouldBe Right("mytarget")
        mountJson.downField("type").as[String] shouldBe Right("bind")
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", mounts = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("mounts").as[Json] shouldBe a[Left[_, _]]
      }
    }

    "vscode plugins field" - {
      val genVscodePlugins: Gen[List[String]] =
        Gen.nonEmptyListOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "appears in customizations.vscode.extensions array" in {
        forAll(genVscodePlugins) { plugins =>
          val config =
            ProjectConfig(name = "test", plugins = Plugins(vscode = plugins, intellij = Nil))
          val json = Config.configAsJson(config, Nil).get

          val extensionsJson = json.hcursor
            .downField("customizations")
            .downField("vscode")
            .downField("extensions")
            .as[List[String]]

          extensionsJson shouldBe Right(plugins)
        }
      }

      "is empty array when no plugins specified" in {
        val config = ProjectConfig(name = "test", plugins = Plugins.empty)
        val json   = Config.configAsJson(config, Nil).get

        val extensionsJson = json.hcursor
          .downField("customizations")
          .downField("vscode")
          .downField("extensions")
          .as[List[String]]

        extensionsJson shouldBe Right(Nil)
      }
    }

    "intellij plugins field" - {
      val genIntellijPlugins: Gen[List[String]] =
        Gen.nonEmptyListOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "appears in customizations.jetbrains.plugins array" in {
        forAll(genIntellijPlugins) { plugins =>
          val config =
            ProjectConfig(name = "test", plugins = Plugins(vscode = Nil, intellij = plugins))
          val json = Config.configAsJson(config, Nil).get

          val pluginsJson = json.hcursor
            .downField("customizations")
            .downField("jetbrains")
            .downField("plugins")
            .as[List[String]]

          pluginsJson shouldBe Right(plugins)
        }
      }

      "is empty array when no plugins specified" in {
        val config = ProjectConfig(name = "test", plugins = Plugins.empty)
        val json   = Config.configAsJson(config, Nil).get

        val pluginsJson = json.hcursor
          .downField("customizations")
          .downField("jetbrains")
          .downField("plugins")
          .as[List[String]]

        pluginsJson shouldBe Right(Nil)
      }
    }

    "containerEnv field" - {
      val genEnv: Gen[Env] = for {
        name  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        value <- Gen.alphaNumStr
      } yield Env(name, value)

      val genEnvList: Gen[List[Env]] = Gen.nonEmptyListOf(genEnv)

      "appears in JSON containerEnv object" in {
        forAll(genEnvList) { envVars =>
          val config = ProjectConfig(name = "test", containerEnv = envVars)
          val json   = Config.configAsJson(config, Nil).get

          val containerEnvJson = json.hcursor.downField("containerEnv").as[Map[String, String]]
          containerEnvJson shouldBe a[Right[_, _]]

          // Check that each env var is present
          envVars.foreach { env =>
            json.hcursor.downField("containerEnv").downField(env.name).as[String] shouldBe Right(
              env.value
            )
          }
        }
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", containerEnv = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("containerEnv").as[Json] shouldBe a[Left[_, _]]
      }
    }

    "remoteEnv field" - {
      val genEnv: Gen[Env] = for {
        name  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        value <- Gen.alphaNumStr
      } yield Env(name, value)

      val genEnvList: Gen[List[Env]] = Gen.nonEmptyListOf(genEnv)

      "appears in JSON remoteEnv object" in {
        forAll(genEnvList) { envVars =>
          val config = ProjectConfig(name = "test", remoteEnv = envVars)
          val json   = Config.configAsJson(config, Nil).get

          val remoteEnvJson = json.hcursor.downField("remoteEnv").as[Map[String, String]]
          remoteEnvJson shouldBe a[Right[_, _]]

          // Check that each env var is present
          envVars.foreach { env =>
            json.hcursor.downField("remoteEnv").downField(env.name).as[String] shouldBe Right(
              env.value
            )
          }
        }
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", remoteEnv = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("remoteEnv").as[Json] shouldBe a[Left[_, _]]
      }
    }

    "postCreateCommand field" - {
      val genCommand: Gen[Command] = for {
        cmd     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        workDir <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      } yield Command(cmd, workDir)

      val genCommands: Gen[List[Command]] = Gen.nonEmptyListOf(genCommand)

      "appears as combined string in JSON" in {
        forAll(genCommands) { commands =>
          val config = ProjectConfig(name = "test", postCreateCommand = commands)
          val json   = Config.configAsJson(config, Nil).get

          val commandJson = json.hcursor.downField("postCreateCommand").as[String]
          commandJson shouldBe a[Right[_, _]]

          // Verify it's not empty
          commandJson.map(_.nonEmpty) shouldBe Right(true)

          // Verify all commands are present in the combined string
          commands.foreach { cmd =>
            commandJson.map(_.contains(cmd.cmd)) shouldBe Right(true)
            commandJson.map(_.contains(cmd.workingDirectory)) shouldBe Right(true)
          }
        }
      }

      "combines multiple commands with && separator" in {
        val commands = List(
          Command("echo hello", "/workspace"),
          Command("echo world", "/tmp")
        )
        val config = ProjectConfig(name = "test", postCreateCommand = commands)
        val json   = Config.configAsJson(config, Nil).get

        val commandJson = json.hcursor.downField("postCreateCommand").as[String]
        commandJson.map(_.contains("&&")) shouldBe Right(true)
      }

      "wraps each command in cd and parentheses" in {
        val command = Command("npm install", "/app")
        val config  = ProjectConfig(name = "test", postCreateCommand = List(command))
        val json    = Config.configAsJson(config, Nil).get

        val commandJson = json.hcursor.downField("postCreateCommand").as[String]
        commandJson shouldBe a[Right[_, _]]
        commandJson.map(_ should include("(cd /app && npm install)"))
      }

      "pipe the output to a useful log file" in {
        val command = Command("npm install", "/app")
        val config  = ProjectConfig(name = "test", postCreateCommand = List(command))
        val json    = Config.configAsJson(config, Nil).get

        val commandJson = json.hcursor.downField("postCreateCommand").as[String]
        commandJson shouldBe a[Right[_, _]]
        commandJson.map(_ should endWith(s"sudo tee /var/log/${Config.postCreateLogName}"))
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", postCreateCommand = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("postCreateCommand").as[String] shouldBe a[Left[_, _]]
      }
    }

    "postStartCommand field" - {
      val genCommand: Gen[Command] = for {
        cmd     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        workDir <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      } yield Command(cmd, workDir)

      val genCommands: Gen[List[Command]] = Gen.nonEmptyListOf(genCommand)

      "appears as combined string in JSON" in {
        forAll(genCommands) { commands =>
          val config = ProjectConfig(name = "test", postStartCommand = commands)
          val json   = Config.configAsJson(config, Nil).get

          val commandJson = json.hcursor.downField("postStartCommand").as[String]
          commandJson shouldBe a[Right[_, _]]

          // Verify all commands are present
          commands.foreach { cmd =>
            commandJson.map(_.contains(cmd.cmd)) shouldBe Right(true)
            commandJson.map(_.contains(cmd.workingDirectory)) shouldBe Right(true)
          }
        }
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", postStartCommand = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("postStartCommand").as[String] shouldBe a[Left[_, _]]
      }
    }

    "forwardPorts field" - {
      val genPort: Gen[Int]                      = Gen.choose(1, 65535)
      val genSamePort: Gen[ForwardPort.SamePort] = genPort.map(ForwardPort.SamePort(_))
      val genDifferentPorts: Gen[ForwardPort.DifferentPorts] = for {
        hostPort      <- genPort
        containerPort <- genPort
      } yield ForwardPort.DifferentPorts(hostPort, containerPort)

      val genForwardPort: Gen[ForwardPort]        = Gen.oneOf(genSamePort, genDifferentPorts)
      val genForwardPorts: Gen[List[ForwardPort]] = Gen.listOf(genForwardPort)

      "appears in JSON forwardPorts array" in {
        forAll(genForwardPorts) { ports =>
          val config = ProjectConfig(name = "test", forwardPorts = ports)
          val json   = Config.configAsJson(config, Nil).get

          val portsJson = json.hcursor.downField("forwardPorts").as[List[Json]]
          portsJson shouldBe a[Right[_, _]]
          portsJson.map(_.size) shouldBe Right(ports.size)
        }
      }

      "SamePort appears as integer in JSON" in {
        val port   = ForwardPort.SamePort(8080)
        val config = ProjectConfig(name = "test", forwardPorts = List(port))
        val json   = Config.configAsJson(config, Nil).get

        val portJson = json.hcursor.downField("forwardPorts").downN(0).as[Int]
        portJson shouldBe Right(8080)
      }

      "DifferentPorts appears as string in JSON" in {
        val port   = ForwardPort.DifferentPorts(8000, 9000)
        val config = ProjectConfig(name = "test", forwardPorts = List(port))
        val json   = Config.configAsJson(config, Nil).get

        val portJson = json.hcursor.downField("forwardPorts").downN(0).as[String]
        portJson shouldBe Right("8000:9000")
      }

      "is empty array when no ports specified" in {
        val config = ProjectConfig(name = "test", forwardPorts = Nil)
        val json   = Config.configAsJson(config, Nil).get

        val portsJson = json.hcursor.downField("forwardPorts").as[List[Json]]
        portsJson shouldBe Right(Nil)
      }
    }

    "capAdd field" - {
      val genCapAdd: Gen[List[String]] =
        Gen.nonEmptyListOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "appears in JSON capAdd array" in {
        forAll(genCapAdd) { capabilities =>
          val config = ProjectConfig(name = "test", capAdd = capabilities)
          val json   = Config.configAsJson(config, Nil).get

          val capAddJson = json.hcursor.downField("capAdd").as[List[String]]
          capAddJson shouldBe Right(capabilities)
        }
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", capAdd = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("capAdd").as[Json] shouldBe a[Left[_, _]]
      }
    }

    "securityOpt field" - {
      val genSecurityOpt: Gen[List[String]] =
        Gen.nonEmptyListOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "appears in JSON securityOpt array" in {
        forAll(genSecurityOpt) { securityOptions =>
          val config = ProjectConfig(name = "test", securityOpt = securityOptions)
          val json   = Config.configAsJson(config, Nil).get

          val securityOptJson = json.hcursor.downField("securityOpt").as[List[String]]
          securityOptJson shouldBe Right(securityOptions)
        }
      }

      "is omitted when empty" in {
        val config = ProjectConfig(name = "test", securityOpt = Nil)
        val json   = Config.configAsJson(config, Nil).get

        json.hcursor.downField("securityOpt").as[Json] shouldBe a[Left[_, _]]
      }
    }
  }
}
