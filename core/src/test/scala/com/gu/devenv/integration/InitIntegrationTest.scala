package com.gu.devenv.integration

import cats.syntax.all.*
import com.gu.devenv.Devenv
import com.gu.devenv.Filesystem.{FileSystemStatus, GitignoreStatus}
import com.gu.devenv.integration.IntegrationTestHelpers.{tempDir, testModules}
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class InitIntegrationTest extends AnyFreeSpec with Matchers with TryValues {

  "init" - {
    "initializing an empty (non-existent) directory" - {
      "should create all required directories and files" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")

          val result = Devenv.init(devcontainerDir, modules).success.value

          result.devcontainerStatus shouldBe FileSystemStatus.Created
          result.userStatus shouldBe FileSystemStatus.Created
          result.sharedStatus shouldBe FileSystemStatus.Created
          result.gitignoreStatus shouldBe GitignoreStatus.Created
          result.devenvStatus shouldBe FileSystemStatus.Created

          Files.exists(devcontainerDir) shouldBe true
          Files.exists(devcontainerDir.resolve("user")) shouldBe true
          Files.exists(devcontainerDir.resolve("shared")) shouldBe true
          Files.exists(devcontainerDir.resolve(".gitignore")) shouldBe true
          Files.exists(devcontainerDir.resolve("devenv.yaml")) shouldBe true
        }
      }

      "should create .gitignore with user/ entry" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir, modules).success.value

          val gitignoreContent = Files.readString(devcontainerDir.resolve(".gitignore"))
          gitignoreContent should include("user/")
          gitignoreContent should include("User-specific devcontainer directory")
        }
      }

      "should create devenv.yaml with placeholder name" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")

          Devenv.init(devcontainerDir, modules).success.value

          val devenvContent = Files.readString(devcontainerDir.resolve("devenv.yaml"))
          devenvContent should include("name: \"CHANGE_ME\"")
          devenvContent should include("modules:")
        }
      }
    }

    "initializing an already initialized directory" - {
      "should report all items as already existing" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")

          // First initialization
          Devenv.init(devcontainerDir, modules).success.value

          // Second initialization
          val result = Devenv.init(devcontainerDir, modules).success.value

          result.devcontainerStatus shouldBe FileSystemStatus.AlreadyExists
          result.userStatus shouldBe FileSystemStatus.AlreadyExists
          result.sharedStatus shouldBe FileSystemStatus.AlreadyExists
          result.gitignoreStatus shouldBe GitignoreStatus.AlreadyExistsWithExclusion
          result.devenvStatus shouldBe FileSystemStatus.AlreadyExists
        }
      }

      "should not modify existing files" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")

          // First initialization
          Devenv.init(devcontainerDir, modules).success.value

          // Modify the devenv.yaml file
          val devenvFile    = devcontainerDir.resolve("devenv.yaml")
          val customContent = "name: \"MyProject\"\nmodules: []"
          Files.writeString(devenvFile, customContent)

          // Second initialization
          Devenv.init(devcontainerDir, modules).success.value

          // File should still have custom content
          Files.readString(devenvFile) shouldBe customContent
        }
      }
    }

    "initializing a directory with a .gitignore file that does not include user/ entry" - {
      "should update the .gitignore file with user/ entry" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")
          Files.createDirectories(devcontainerDir)

          // Create a .gitignore without user/ entry
          val gitignoreFile   = devcontainerDir.resolve(".gitignore")
          val existingContent = "*.log\n*.tmp\n"
          Files.writeString(gitignoreFile, existingContent)

          val result = Devenv.init(devcontainerDir, modules).success.value

          result.gitignoreStatus shouldBe GitignoreStatus.Updated

          val updatedContent = Files.readString(gitignoreFile)
          updatedContent should startWith(existingContent)
          updatedContent should include("user/")
          updatedContent should include("User-specific devcontainer directory")
        }
      }

      "should preserve existing .gitignore content when updating" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")
          Files.createDirectories(devcontainerDir)

          val gitignoreFile   = devcontainerDir.resolve(".gitignore")
          val existingContent = "# My custom gitignore\n*.log\ntarget/\n"
          Files.writeString(gitignoreFile, existingContent)

          Devenv.init(devcontainerDir, modules).success.value

          val updatedContent = Files.readString(gitignoreFile)
          updatedContent should include("*.log")
          updatedContent should include("target/")
          updatedContent should include("# My custom gitignore")
        }
      }
    }

    "initializing a directory with a .gitignore file that already includes user/ entry" - {
      "should report gitignore as already existing with exclusion" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")
          Files.createDirectories(devcontainerDir)

          val gitignoreFile   = devcontainerDir.resolve(".gitignore")
          val existingContent = "*.log\nuser/\n*.tmp\n"
          Files.writeString(gitignoreFile, existingContent)

          val result = Devenv.init(devcontainerDir, modules).success.value

          result.gitignoreStatus shouldBe GitignoreStatus.AlreadyExistsWithExclusion
        }
      }

      "should not modify the existing .gitignore file" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")
          Files.createDirectories(devcontainerDir)

          val gitignoreFile   = devcontainerDir.resolve(".gitignore")
          val existingContent = "*.log\nuser/\n*.tmp\n"
          Files.writeString(gitignoreFile, existingContent)

          Devenv.init(devcontainerDir, modules).success.value

          Files.readString(gitignoreFile) shouldBe existingContent
        }
      }

      "should recognize user/ entry with whitespace" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")
          Files.createDirectories(devcontainerDir)

          val gitignoreFile   = devcontainerDir.resolve(".gitignore")
          val existingContent = "*.log\n  user/  \n*.tmp\n"
          Files.writeString(gitignoreFile, existingContent)

          val result = Devenv.init(devcontainerDir, modules).success.value

          result.gitignoreStatus shouldBe GitignoreStatus.AlreadyExistsWithExclusion
        }
      }
    }

    "initializing a directory with partial initialization" - {
      "should create missing directories and files only" in {
        (tempDir, testModules).mapN { (rootDir, modules) =>
          val devcontainerDir = rootDir.resolve(".devcontainer")
          Files.createDirectories(devcontainerDir)
          Files.createDirectories(devcontainerDir.resolve("user"))

          val result = Devenv.init(devcontainerDir, modules).success.value

          result.devcontainerStatus shouldBe FileSystemStatus.AlreadyExists
          result.userStatus shouldBe FileSystemStatus.AlreadyExists
          result.sharedStatus shouldBe FileSystemStatus.Created
          result.gitignoreStatus shouldBe GitignoreStatus.Created
          result.devenvStatus shouldBe FileSystemStatus.Created
        }
      }
    }
  }
}
