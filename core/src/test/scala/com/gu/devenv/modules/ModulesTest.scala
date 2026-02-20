package com.gu.devenv.modules

import com.gu.devenv.*
import com.gu.devenv.modules.Modules.ModuleContribution
import io.circe.Json
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ModulesTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  "Modules.applyModuleContribution" - {

    "features field" - {
      val genFeatures: Gen[Map[String, Json]] = for {
        size <- Gen.choose(0, 5)
        keys <- Gen.listOfN(size, Gen.alphaNumStr.suchThat(_.nonEmpty))
        values <- Gen.listOfN(
          size,
          Gen.oneOf(
            Gen.const(Json.obj()),
            Gen.alphaNumStr.map(Json.fromString),
            Gen.choose(0, 100).map(Json.fromInt),
            Gen.const(Json.True),
            Gen.const(Json.False)
          )
        )
      } yield keys.zip(values).toMap

      "adds features to empty config" in {
        forAll(genFeatures) { features =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(features = features)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // All features from contribution should be in result
          features.keys.foreach { key =>
            result.features should contain key key
            result.features(key) shouldBe features(key)
          }
        }
      }

      "merges features with existing config (explicit features take precedence)" in {
        forAll(genFeatures, genFeatures) { (moduleFeatures, explicitFeatures) =>
          val baseConfig   = ProjectConfig(name = "test", features = explicitFeatures)
          val contribution = ModuleContribution(features = moduleFeatures)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Explicit features should win for overlapping keys
          explicitFeatures.keys.foreach { key =>
            result.features(key) shouldBe explicitFeatures(key)
          }

          // Module features should be present for non-overlapping keys
          (moduleFeatures.keySet -- explicitFeatures.keySet).foreach { key =>
            result.features(key) shouldBe moduleFeatures(key)
          }
        }
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

      val genMounts: Gen[List[Mount]] = Gen.listOf(genMount)

      "adds mounts to empty config" in {
        forAll(genMounts) { mounts =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(mounts = mounts)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.mounts shouldBe mounts
        }
      }

      "prepends mounts to existing config" in {
        forAll(genMounts, genMounts) { (moduleMounts, existingMounts) =>
          val baseConfig   = ProjectConfig(name = "test", mounts = existingMounts)
          val contribution = ModuleContribution(mounts = moduleMounts)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module mounts should come first
          result.mounts shouldBe (moduleMounts ++ existingMounts)
        }
      }
    }

    "vscode plugins field" - {
      val genVscodePlugins: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds vscode plugins to empty config" in {
        forAll(genVscodePlugins) { plugins =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(plugins = Plugins(vscode = plugins, intellij = Nil))

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.plugins.vscode shouldBe plugins
        }
      }

      "prepends vscode plugins to existing config" in {
        forAll(genVscodePlugins, genVscodePlugins) { (modulePlugins, existingPlugins) =>
          val baseConfig =
            ProjectConfig(
              name = "test",
              plugins = Plugins(vscode = existingPlugins, intellij = Nil)
            )
          val contribution =
            ModuleContribution(plugins = Plugins(vscode = modulePlugins, intellij = Nil))

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module plugins should come first
          result.plugins.vscode shouldBe (modulePlugins ++ existingPlugins)
        }
      }
    }

    "intellij plugins field" - {
      val genIntellijPlugins: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds intellij plugins to empty config" in {
        forAll(genIntellijPlugins) { plugins =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(plugins = Plugins(vscode = Nil, intellij = plugins))

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.plugins.intellij shouldBe plugins
        }
      }

      "prepends intellij plugins to existing config" in {
        forAll(genIntellijPlugins, genIntellijPlugins) { (modulePlugins, existingPlugins) =>
          val baseConfig =
            ProjectConfig(
              name = "test",
              plugins = Plugins(vscode = Nil, intellij = existingPlugins)
            )
          val contribution =
            ModuleContribution(plugins = Plugins(vscode = Nil, intellij = modulePlugins))

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module plugins should come first
          result.plugins.intellij shouldBe (modulePlugins ++ existingPlugins)
        }
      }
    }

    "containerEnv field" - {
      val genEnv: Gen[Env] = for {
        name  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        value <- Gen.alphaNumStr
      } yield Env(name, value)

      val genEnvList: Gen[List[Env]] = Gen.listOf(genEnv)

      "adds containerEnv to empty config" in {
        forAll(genEnvList) { envVars =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(containerEnv = envVars)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.containerEnv shouldBe envVars
        }
      }

      "prepends containerEnv to existing config" in {
        forAll(genEnvList, genEnvList) { (moduleEnv, existingEnv) =>
          val baseConfig   = ProjectConfig(name = "test", containerEnv = existingEnv)
          val contribution = ModuleContribution(containerEnv = moduleEnv)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module env should come first
          result.containerEnv shouldBe (moduleEnv ++ existingEnv)
        }
      }
    }

    "remoteEnv field" - {
      val genEnv: Gen[Env] = for {
        name  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        value <- Gen.alphaNumStr
      } yield Env(name, value)

      val genEnvList: Gen[List[Env]] = Gen.listOf(genEnv)

      "adds remoteEnv to empty config" in {
        forAll(genEnvList) { envVars =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(remoteEnv = envVars)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.remoteEnv shouldBe envVars
        }
      }

      "prepends remoteEnv to existing config" in {
        forAll(genEnvList, genEnvList) { (moduleEnv, existingEnv) =>
          val baseConfig   = ProjectConfig(name = "test", remoteEnv = existingEnv)
          val contribution = ModuleContribution(remoteEnv = moduleEnv)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module env should come first
          result.remoteEnv shouldBe (moduleEnv ++ existingEnv)
        }
      }
    }

    "postCreateCommands field" - {
      val genCommand: Gen[Command] = for {
        cmd     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        workDir <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      } yield Command(cmd, workDir)

      val genCommands: Gen[List[Command]] = Gen.listOf(genCommand)

      "adds postCreateCommands to empty config" in {
        forAll(genCommands) { commands =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(postCreateCommands = commands)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.postCreateCommand shouldBe commands
        }
      }

      "prepends postCreateCommands to existing config" in {
        forAll(genCommands, genCommands) { (moduleCommands, existingCommands) =>
          val baseConfig   = ProjectConfig(name = "test", postCreateCommand = existingCommands)
          val contribution = ModuleContribution(postCreateCommands = moduleCommands)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module commands should come first
          result.postCreateCommand shouldBe (moduleCommands ++ existingCommands)
        }
      }
    }

    "capAdd field" - {
      val genCapAdd: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds capAdd to empty config" in {
        forAll(genCapAdd) { capabilities =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(capAdd = capabilities)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.capAdd shouldBe capabilities
        }
      }

      "prepends capAdd to existing config" in {
        forAll(genCapAdd, genCapAdd) { (moduleCapAdd, existingCapAdd) =>
          val baseConfig   = ProjectConfig(name = "test", capAdd = existingCapAdd)
          val contribution = ModuleContribution(capAdd = moduleCapAdd)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module capabilities should come first
          result.capAdd shouldBe (moduleCapAdd ++ existingCapAdd)
        }
      }
    }

    "securityOpt field" - {
      val genSecurityOpt: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds securityOpt to empty config" in {
        forAll(genSecurityOpt) { securityOptions =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(securityOpt = securityOptions)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.securityOpt shouldBe securityOptions
        }
      }

      "prepends securityOpt to existing config" in {
        forAll(genSecurityOpt, genSecurityOpt) { (moduleSecurityOpt, existingSecurityOpt) =>
          val baseConfig   = ProjectConfig(name = "test", securityOpt = existingSecurityOpt)
          val contribution = ModuleContribution(securityOpt = moduleSecurityOpt)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module security options should come first
          result.securityOpt shouldBe (moduleSecurityOpt ++ existingSecurityOpt)
        }
      }
    }
  }
}
