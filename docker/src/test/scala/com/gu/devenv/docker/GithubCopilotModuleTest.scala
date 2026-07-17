package com.gu.devenv.docker

import com.gu.devenv.docker.testutils.{ContainerTest, DevcontainerTestSupport}
import com.gu.devenv.docker.verifiers.GithubCopilotVerifier
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

/** Docker integration tests for the github copilot module.
  *
  * Verifies that the GitHub CLI and GitHub Copilot CLI are installed and available. The module
  * relies on mise to install these tools, so the fixture enables both the mise and github-copilot
  * modules.
  */
class GithubCopilotModuleTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {
  "github copilot module" - {
    "can set up workspace from fixture" in {
      val workspace = setupWorkspaceWithSmallContainer("github-copilot")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    "devenv generation works" in {
      val workspace = setupWorkspaceWithSmallContainer("github-copilot")
      runDevenvGenerate(workspace) match {
        case Left(error) =>
          fail(s"Generation failed: $error")
        case Right(_) =>
          Files.exists(workspace.resolve(".devcontainer/shared/devcontainer.json")) shouldBe true
          Files.exists(workspace.resolve(".devcontainer/user/devcontainer.json")) shouldBe true
      }
    }

    "should install the GitHub CLI and Copilot CLI" taggedAs ContainerTest in {
      val workspace = setupWorkspaceWithSmallContainer("github-copilot")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          GithubCopilotVerifier.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }
  }
}

