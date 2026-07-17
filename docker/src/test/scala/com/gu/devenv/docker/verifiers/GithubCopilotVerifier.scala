package com.gu.devenv.docker.verifiers

import com.gu.devenv.docker.testutils.DevcontainerRunner

/** Verifies that the github copilot module has been installed correctly.
  *
  * The module installs the GitHub CLI (`gh`) and the GitHub Copilot CLI (`copilot`) via mise, so we
  * check both are on the PATH and runnable.
  */
object GithubCopilotVerifier {
  def verify(runner: DevcontainerRunner): Either[String, Unit] =
    for {
      _ <- checkGitHubCliInstalled(runner)
      _ <- checkCopilotCliInstalled(runner)
    } yield ()

  private def checkGitHubCliInstalled(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec("gh --version")
    if (result.succeeded) Right(())
    else Left(s"GitHub CLI (gh) is not installed or not on PATH: ${result.combinedOutput}")
  }

  private def checkCopilotCliInstalled(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec("copilot --version")
    if (result.succeeded) Right(())
    else
      Left(
        s"GitHub Copilot CLI (copilot) is not installed or not on PATH: ${result.combinedOutput}"
      )
  }
}
