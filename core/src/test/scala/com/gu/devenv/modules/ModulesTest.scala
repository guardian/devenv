package com.gu.devenv.modules

import com.gu.devenv.*
import com.gu.devenv.modules.Modules.{Module, ModuleContribution, ModuleResolutionError}
import io.circe.Json

import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class ModulesTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  def testModule(name: String, dependsOn: List[String] = Nil): Module =
    Module(
      name = name,
      summary = s"$name module",
      enabledByDefault = false,
      contribution = ModuleContribution(),
      dependsOn = dependsOn
    )

  "Modules.applyModuleContribution" - {

    "features field" - {
      val genFeatures: Gen[Map[String, Json]] = for {
        size   <- Gen.choose(0, 5)
        keys   <- Gen.listOfN(size, Gen.alphaNumStr.suchThat(_.nonEmpty))
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

      "adds features to empty config" in
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

      "merges features with existing config (explicit features take precedence)" in
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

      "adds mounts to empty config" in
        forAll(genMounts) { mounts =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(mounts = mounts)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.mounts shouldBe mounts
        }

      "prepends mounts to existing config" in
        forAll(genMounts, genMounts) { (moduleMounts, existingMounts) =>
          val baseConfig   = ProjectConfig(name = "test", mounts = existingMounts)
          val contribution = ModuleContribution(mounts = moduleMounts)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module mounts should come first
          result.mounts shouldBe (moduleMounts ++ existingMounts)
        }
    }

    "vscode plugins field" - {
      val genVscodePlugins: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds vscode plugins to empty config" in
        forAll(genVscodePlugins) { plugins =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(plugins = Plugins(vscode = plugins, intellij = Nil))

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.plugins.vscode shouldBe plugins
        }

      "prepends vscode plugins to existing config" in
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

    "intellij plugins field" - {
      val genIntellijPlugins: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds intellij plugins to empty config" in
        forAll(genIntellijPlugins) { plugins =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(plugins = Plugins(vscode = Nil, intellij = plugins))

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.plugins.intellij shouldBe plugins
        }

      "prepends intellij plugins to existing config" in
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

    "containerEnv field" - {
      val genEnv: Gen[Env] = for {
        name  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        value <- Gen.alphaNumStr
      } yield Env(name, value)

      val genEnvList: Gen[List[Env]] = Gen.listOf(genEnv)

      "adds containerEnv to empty config" in
        forAll(genEnvList) { envVars =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(containerEnv = envVars)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.containerEnv shouldBe envVars
        }

      "prepends containerEnv to existing config" in
        forAll(genEnvList, genEnvList) { (moduleEnv, existingEnv) =>
          val baseConfig   = ProjectConfig(name = "test", containerEnv = existingEnv)
          val contribution = ModuleContribution(containerEnv = moduleEnv)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module env should come first
          result.containerEnv shouldBe (moduleEnv ++ existingEnv)
        }
    }

    "remoteEnv field" - {
      val genEnv: Gen[Env] = for {
        name  <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        value <- Gen.alphaNumStr
      } yield Env(name, value)

      val genEnvList: Gen[List[Env]] = Gen.listOf(genEnv)

      "adds remoteEnv to empty config" in
        forAll(genEnvList) { envVars =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(remoteEnv = envVars)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.remoteEnv shouldBe envVars
        }

      "prepends remoteEnv to existing config" in
        forAll(genEnvList, genEnvList) { (moduleEnv, existingEnv) =>
          val baseConfig   = ProjectConfig(name = "test", remoteEnv = existingEnv)
          val contribution = ModuleContribution(remoteEnv = moduleEnv)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module env should come first
          result.remoteEnv shouldBe (moduleEnv ++ existingEnv)
        }
    }

    "onCreateCommands field" - {
      val genCommand: Gen[Command] = for {
        cmd     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        workDir <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      } yield Command(cmd, workDir, Some("onCreateCommands"))

      val genCommands: Gen[List[Command]] = Gen.listOf(genCommand)

      "adds onCreateCommands to empty config" in
        forAll(genCommands) { commands =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(onCreateCommands = commands)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.onCreateCommand shouldBe commands
        }

      "prepends onCreateCommands to existing config" in
        forAll(genCommands, genCommands) { (moduleCommands, existingCommands) =>
          val baseConfig   = ProjectConfig(name = "test", onCreateCommand = existingCommands)
          val contribution = ModuleContribution(onCreateCommands = moduleCommands)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module commands should come first
          result.onCreateCommand shouldBe (moduleCommands ++ existingCommands)
        }
    }

    "postCreateCommands field" - {
      val genCommand: Gen[Command] = for {
        cmd     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
        workDir <- Gen.alphaNumStr.suchThat(_.nonEmpty)
      } yield Command(cmd, workDir, Some("postCreateCommands"))

      val genCommands: Gen[List[Command]] = Gen.listOf(genCommand)

      "adds postCreateCommands to empty config" in
        forAll(genCommands) { commands =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(postCreateCommands = commands)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.postCreateCommand shouldBe commands
        }

      "prepends postCreateCommands to existing config" in
        forAll(genCommands, genCommands) { (moduleCommands, existingCommands) =>
          val baseConfig   = ProjectConfig(name = "test", postCreateCommand = existingCommands)
          val contribution = ModuleContribution(postCreateCommands = moduleCommands)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module commands should come first
          result.postCreateCommand shouldBe (moduleCommands ++ existingCommands)
        }
    }

    "capAdd field" - {
      val genCapAdd: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds capAdd to empty config" in
        forAll(genCapAdd) { capabilities =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(capAdd = capabilities)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.capAdd shouldBe capabilities
        }

      "prepends capAdd to existing config" in
        forAll(genCapAdd, genCapAdd) { (moduleCapAdd, existingCapAdd) =>
          val baseConfig   = ProjectConfig(name = "test", capAdd = existingCapAdd)
          val contribution = ModuleContribution(capAdd = moduleCapAdd)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module capabilities should come first
          result.capAdd shouldBe (moduleCapAdd ++ existingCapAdd)
        }
    }

    "securityOpt field" - {
      val genSecurityOpt: Gen[List[String]] =
        Gen.listOf(Gen.alphaNumStr.suchThat(_.nonEmpty))

      "adds securityOpt to empty config" in
        forAll(genSecurityOpt) { securityOptions =>
          val baseConfig   = ProjectConfig(name = "test")
          val contribution = ModuleContribution(securityOpt = securityOptions)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          result.securityOpt shouldBe securityOptions
        }

      "prepends securityOpt to existing config" in
        forAll(genSecurityOpt, genSecurityOpt) { (moduleSecurityOpt, existingSecurityOpt) =>
          val baseConfig   = ProjectConfig(name = "test", securityOpt = existingSecurityOpt)
          val contribution = ModuleContribution(securityOpt = moduleSecurityOpt)

          val result = Modules.applyModuleContribution(baseConfig, contribution)

          // Module security options should come first
          result.securityOpt shouldBe (moduleSecurityOpt ++ existingSecurityOpt)
        }
    }
  }

  "Modules.applyModules" - {
    "leaves the project config unchanged when there are no resolved modules" in {
      val config = ProjectConfig(name = "test", features = Map("explicit" -> Json.True))

      Modules.applyModules(config, Modules.ResolvedModules.empty) shouldBe config
    }

    "applies every resolved module contribution" in {
      val a = testModule("a").copy(
        contribution = ModuleContribution(features = Map("a" -> Json.True))
      )
      val b = testModule("b").copy(
        contribution = ModuleContribution(features = Map("b" -> Json.True))
      )
      val resolvedModules = Modules.resolveModules(List("a", "b"), List(a, b)).toOption.get

      val result = Modules.applyModules(ProjectConfig(name = "test"), resolvedModules)

      result.features shouldBe Map("a" -> Json.True, "b" -> Json.True)
    }

    "gives later configured modules precedence over earlier modules" in {
      val earlier = testModule("earlier").copy(
        contribution = ModuleContribution(features = Map("shared" -> Json.fromString("earlier")))
      )
      val later = testModule("later").copy(
        contribution = ModuleContribution(features = Map("shared" -> Json.fromString("later")))
      )
      val resolvedModules =
        Modules.resolveModules(List("earlier", "later"), List(earlier, later)).toOption.get

      val result = Modules.applyModules(ProjectConfig(name = "test"), resolvedModules)

      result.features("shared") shouldBe Json.fromString("later")
    }

    "gives explicit project config precedence over module contributions" in {
      val configured = testModule("configured").copy(
        contribution = ModuleContribution(features = Map("shared" -> Json.fromString("module")))
      )
      val resolvedModules =
        Modules.resolveModules(List("configured"), List(configured)).toOption.get
      val config = ProjectConfig(
        name = "test",
        features = Map("shared" -> Json.fromString("explicit"))
      )

      val result = Modules.applyModules(config, resolvedModules)

      result.features("shared") shouldBe Json.fromString("explicit")
    }
  }

  "Modules.resolveModules" - {
    "resolves an empty list" in {
      val result = Modules.resolveModules(Nil, Nil)

      result.toOption.get.modules shouldBe Nil
    }

    "resolves configured IDs to modules in configured order" in {
      val a      = testModule("a")
      val b      = testModule("b")
      val unused = testModule("unused")

      val result = Modules.resolveModules(List("b", "a"), List(a, unused, b))

      result.toOption.get.modules shouldBe List(b, a)
    }

    "fails when a configured module ID is unknown" in {
      val result = Modules.resolveModules(List("missing"), List(testModule("available")))

      result shouldBe Left(ModuleResolutionError.UnknownModule("missing"))
    }

    "fails when resolved module dependencies are invalid" in {
      val a      = testModule("a")
      val b      = testModule("b", dependsOn = List("a"))
      val result = Modules.resolveModules(List("b"), List(a, b))

      result shouldBe Left(ModuleResolutionError.DependencyNotEnabled("b", "a"))
    }
  }

  "Modules.validateDependencies" - {

    "success cases" - {
      "succeeds with empty project and available module lists" in {
        Modules.validateDependencies(Nil, Nil) shouldBe Right(())
      }

      "succeeds when no module has dependencies" in {
        val a         = testModule("a")
        val b         = testModule("b")
        val available = List(a, b)

        Modules.validateDependencies(List(a, b), available) shouldBe Right(())
      }

      "succeeds when a dependency is enabled and appears earlier in the list" in {
        val a         = testModule("a")
        val b         = testModule("b", dependsOn = List("a"))
        val available = List(a, b)

        Modules.validateDependencies(List(a, b), available) shouldBe Right(())
      }

      "succeeds with a chain of dependencies in order" in {
        val a         = testModule("a")
        val b         = testModule("b", dependsOn = List("a"))
        val c         = testModule("c", dependsOn = List("a", "b"))
        val available = List(a, b, c)

        Modules.validateDependencies(List(a, b, c), available) shouldBe Right(())
      }
    }

    "error cases" - {
      "fails when a module depends on an unknown module" in {
        val a      = testModule("a", dependsOn = List("missing"))
        val result = Modules.validateDependencies(List(a), List(a))

        result shouldBe Left(ModuleResolutionError.UnknownDependency("a", "missing"))
      }

      "fails when a dependency exists but is not enabled in the project" in {
        val a      = testModule("a")
        val b      = testModule("b", dependsOn = List("a"))
        val result = Modules.validateDependencies(List(b), List(a, b))

        result shouldBe Left(ModuleResolutionError.DependencyNotEnabled("b", "a"))
      }

      "fails when a dependency is enabled but appears after the dependent module" in {
        val a      = testModule("a")
        val b      = testModule("b", dependsOn = List("a"))
        val result = Modules.validateDependencies(List(b, a), List(a, b))

        result shouldBe Left(ModuleResolutionError.DependencyOutOfOrder("b", "a"))
      }
    }
  }
}
