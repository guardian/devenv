package com.gu.devenv.docker

import com.gu.devenv.docker.testutils.{ContainerTest, DevcontainerTestSupport}
import com.gu.devenv.docker.verifiers.AgentsyVerifier
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

class AgentsyModuleTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {
  "agentsy module" - {
    "can set up workspace from fixture" in {
      val workspace = setupWorkspace("agentsy")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    "devenv generation works" in {
      val workspace = setupWorkspace("agentsy")

      runDevenvGenerate(workspace) match {
        case Left(error) => fail(s"Generation failed: $error")
        case Right(_)    =>
          Files.exists(workspace.resolve(".devcontainer/shared/devcontainer.json")) shouldBe true
          Files.exists(workspace.resolve(".devcontainer/user/devcontainer.json")) shouldBe true
      }
    }

    // TODO: Test the rebuild logic that does a fetch and merge
    //       This would require adding support to rebuild devcontainers in DevcontainerTestSupport
    "should install Agentsy and make it available" taggedAs ContainerTest in {
      val workspace = setupWorkspace("agentsy")

      startContainer(workspace) match {
        case Left(error)   => fail(s"Failed to start container: $error")
        case Right(runner) =>
          AgentsyVerifier.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }
  }
}
