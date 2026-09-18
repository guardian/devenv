package com.gu.devenv.docker.verifiers

import com.gu.devenv.docker.testutils.DevcontainerRunner

object AgentsyVerifier {
  def verify(runner: DevcontainerRunner): Either[String, Unit] =
    for {
      _ <- checkAgentsyInstalled(runner)
      _ <- checkAgentsyWorks(runner)
    } yield ()

  private def checkAgentsyInstalled(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec("command -v agentsy")
    if (result.succeeded) Right(())
    else Left(s"Agentsy is not installed or not on PATH: ${result.combinedOutput}")
  }

  private def checkAgentsyWorks(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec("agentsy list")
    if (result.succeeded) Right(())
    else Left(s"Agentsy list command failed: ${result.combinedOutput}")
  }
}
