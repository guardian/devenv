package com.gu.devenv.docker

import com.gu.devenv.docker.verifiers.{DockerInDockerVerifier, MiseVerifier}
import com.gu.devenv.docker.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

/** Docker integration tests for using multiple modules together.
  *
  * Verifies that all modules work correctly when combined.
  */
class CombinedModulesTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {
  "combined modules" - {
    "can set up workspace from fixture" in {
      val workspace = setupWorkspaceWithSmallContainer("combined")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    "devenv generation works" in {
      val workspace = setupWorkspaceWithSmallContainer("combined")
      runDevenvGenerate(workspace) match {
        case Left(error) =>
          fail(s"Generation failed: $error")
        case Right(_) =>
          Files.exists(workspace.resolve(".devcontainer/shared/devcontainer.json")) shouldBe true
          Files.exists(workspace.resolve(".devcontainer/user/devcontainer.json")) shouldBe true
      }
    }

    "should work with mise and docker-in-docker together" taggedAs ContainerTest in {
      val workspace = setupWorkspaceWithSmallContainer("combined")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          // Verify all modules
          val assertions = List(
            ("mise", MiseVerifier.verify(runner)),
            ("docker-in-docker", DockerInDockerVerifier.verify(runner))
          )

          val failures = assertions.collect { case (name, Left(error)) =>
            s"$name: $error"
          }

          if (failures.nonEmpty) {
            fail(s"Module verification failures:\n${failures.mkString("\n")}")
          } else {
            succeed
          }
      }
    }
  }
}
