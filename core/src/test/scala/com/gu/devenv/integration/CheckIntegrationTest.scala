package com.gu.devenv.integration

import cats.syntax.all.*
import com.gu.devenv.Devenv
import com.gu.devenv.CheckResult
import com.gu.devenv.integration.IntegrationTestHelpers._
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class CheckIntegrationTest extends AnyFreeSpec with Matchers with TryValues {

  "check" - {
    "checking an uninitialized directory" - {
      "should return NotInitialized result" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result shouldBe CheckResult.NotInitialized
        }
      }
    }

    "checking with placeholder project name" - {
      "should return NotInitialized result" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Initialize but don't customize
          Devenv.init(devcontainerDir, modules).success.value

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result shouldBe CheckResult.NotInitialized
        }
      }
    }

    "checking when no devcontainer files exist" - {
      "should return Mismatch for both files" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Initialize and customize but don't generate
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              userMismatch shouldBe defined
              sharedMismatch shouldBe defined
            case CheckResult.Match(_, _) =>
              fail(
                "Expected Mismatch result but got Match (files should not match when they don't exist)"
              )
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }

      "should include correct file paths in mismatch" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, sharedMismatch, userPath, sharedPath) =>
              userMismatch.map(_.path) shouldBe Some(".devcontainer/user/devcontainer.json")
              sharedMismatch.map(_.path) shouldBe Some(".devcontainer/shared/devcontainer.json")
              userPath shouldBe ".devcontainer/user/devcontainer.json"
              sharedPath shouldBe ".devcontainer/shared/devcontainer.json"
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }
    }

    "checking when files match configuration" - {
      "should return Match result" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Initialize, customize, and generate
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Match(_, _) => succeed
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              fail(
                s"Expected Match result but got Mismatch (user: ${userMismatch.isDefined}, shared: ${sharedMismatch.isDefined})"
              )
            case CheckResult.NotInitialized =>
              fail("Expected Match result but got NotInitialized")
          }
        }
      }

      "should include correct file paths in match" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Match(userPath, sharedPath) =>
              userPath shouldBe ".devcontainer/user/devcontainer.json"
              sharedPath shouldBe ".devcontainer/shared/devcontainer.json"
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              fail(
                s"Expected Match result but got Mismatch (user: ${userMismatch.isDefined}, shared: ${sharedMismatch.isDefined})"
              )
            case CheckResult.NotInitialized =>
              fail("Expected Match result but got NotInitialized")
          }
        }
      }
    }

    "checking when only user file is out of date" - {
      "should return Mismatch with only user diff" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Generate files, then modify only user file
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          // Modify user file
          val userFile = devcontainerDir.resolve("user/devcontainer.json")
          Files.writeString(userFile, """{"name": "Modified"}""")

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              userMismatch shouldBe defined
              sharedMismatch shouldBe None
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match (user file was modified)")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }

      "should include the modified file path" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          val userFile = devcontainerDir.resolve("user/devcontainer.json")
          Files.writeString(userFile, """{"name": "Modified"}""")

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, _, _, _) =>
              userMismatch.map(_.path) shouldBe Some(".devcontainer/user/devcontainer.json")
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }
    }

    "checking when only shared file is out of date" - {
      "should return Mismatch with only shared diff" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Generate files, then modify only shared file
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          // Modify shared file
          val sharedFile = devcontainerDir.resolve("shared/devcontainer.json")
          Files.writeString(sharedFile, """{"name": "Modified"}""")

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              userMismatch shouldBe None
              sharedMismatch shouldBe defined
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match (shared file was modified)")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }

      "should include the modified file path" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          val sharedFile = devcontainerDir.resolve("shared/devcontainer.json")
          Files.writeString(sharedFile, """{"name": "Modified"}""")

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(_, sharedMismatch, _, _) =>
              sharedMismatch.map(_.path) shouldBe Some(".devcontainer/shared/devcontainer.json")
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }
    }

    "checking when both files are out of date" - {
      "should return Mismatch with both diffs" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Generate files, then modify both
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          // Modify both files
          val userFile = devcontainerDir.resolve("user/devcontainer.json")
          Files.writeString(userFile, """{"name": "Modified User"}""")
          val sharedFile = devcontainerDir.resolve("shared/devcontainer.json")
          Files.writeString(sharedFile, """{"name": "Modified Shared"}""")

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              userMismatch shouldBe defined
              sharedMismatch shouldBe defined
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match (both files were modified)")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }
    }

    "checking after config change" - {
      "should detect mismatch when config changes" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Generate with one config
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          // Change config
          Files.writeString(devenvFile, projectConfigWithModules)

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(_, _, _, _) => succeed
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match (config was changed)")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }

      "should show Match after regenerating" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Generate, change config, regenerate
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          // Change config and regenerate
          Files.writeString(devenvFile, projectConfigWithModules)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Match(_, _) => succeed
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              fail(
                s"Expected Match result but got Mismatch after regenerating (user: ${userMismatch.isDefined}, shared: ${sharedMismatch.isDefined})"
              )
            case CheckResult.NotInitialized =>
              fail("Expected Match result but got NotInitialized")
          }
        }
      }
    }

    "checking with user config" - {
      "should match when user config is present" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Set up user config
          val userDevenvFile = userConfigDir.resolve("devenv.yaml")
          Files.writeString(userDevenvFile, userConfigWithPlugins)

          // Generate with user config
          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Match(_, _) => succeed
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              fail(
                s"Expected Match result but got Mismatch with user config (user: ${userMismatch.isDefined}, shared: ${sharedMismatch.isDefined})"
              )
            case CheckResult.NotInitialized =>
              fail("Expected Match result but got NotInitialized")
          }
        }
      }

      "should detect mismatch when user config changes" in {
        (tempDir, tempDir, testModules).mapN { (tempDir, userConfigDir, modules) =>
          val devcontainerDir = tempDir.resolve(".devcontainer")

          // Generate with one user config
          val userDevenvFile = userConfigDir.resolve("devenv.yaml")
          Files.writeString(userDevenvFile, userConfigWithPlugins)

          Devenv.init(devcontainerDir, modules).success.value
          val devenvFile = devcontainerDir.resolve("devenv.yaml")
          Files.writeString(devenvFile, basicProjectConfig)
          Devenv.generate(devcontainerDir, userConfigDir, modules).success.value

          // Change user config
          Files.writeString(userDevenvFile, userConfigWithDotfiles)

          val result = Devenv.check(devcontainerDir, userConfigDir, modules).success.value

          result match {
            case CheckResult.Mismatch(userMismatch, sharedMismatch, _, _) =>
              // User file should differ due to changed user config
              userMismatch shouldBe defined
              // Shared file should still match (user config doesn't affect it)
              sharedMismatch shouldBe None
            case CheckResult.Match(_, _) =>
              fail("Expected Mismatch result but got Match (user config was changed)")
            case CheckResult.NotInitialized =>
              fail("Expected Mismatch result but got NotInitialized")
          }
        }
      }
    }
  }
}
