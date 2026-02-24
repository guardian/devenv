package com.gu.devenv.docker

import com.gu.devenv.docker.verifiers.DockerInDockerVerifier
import com.gu.devenv.docker.testutils.{ContainerTest, DevcontainerTestSupport}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Files

/** Docker integration tests for the docker-in-docker module.
  *
  * Verifies that Docker and Docker Compose work inside the container.
  */
class DockerInDockerModuleTest extends AnyFreeSpec with Matchers with DevcontainerTestSupport {

  "docker-in-docker module" - {
    "can set up workspace from fixture" in {
      val workspace = setupWorkspace("docker-in-docker")
      Files.isDirectory(workspace) shouldBe true
      Files.exists(workspace.resolve(".devcontainer/devenv.yaml")) shouldBe true
    }

    "devenv generation works" in {
      val workspace = setupWorkspace("docker-in-docker")
      runDevenvGenerate(workspace) match {
        case Left(error) =>
          fail(s"Generation failed: $error")
        case Right(_) =>
          Files.exists(workspace.resolve(".devcontainer/shared/devcontainer.json")) shouldBe true
          Files.exists(workspace.resolve(".devcontainer/user/devcontainer.json")) shouldBe true
      }
    }

    "should have a working Docker installation" taggedAs ContainerTest in {
      val workspace = setupWorkspace("docker-in-docker", "github-actions-user-config")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          DockerInDockerVerifier.verify(runner) match {
            case Left(error) => fail(error)
            case Right(_)    => succeed
          }
      }
    }

    "should be able to run docker containers" taggedAs ContainerTest in {
      val workspace = setupWorkspace("docker-in-docker")

      startContainer(workspace) match {
        case Left(error) =>
          fail(s"Failed to start container: $error")

        case Right(runner) =>
          val result = runner.exec("docker run --rm hello-world")
          result.succeeded shouldBe true
          result.stdout should include("Hello from Docker!")
      }
    }
  }
}
