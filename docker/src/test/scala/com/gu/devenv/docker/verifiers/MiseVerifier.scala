package com.gu.devenv.docker.verifiers

import com.gu.devenv.docker.testutils.DevcontainerRunner

/** Verifies that mise module has been installed and configured correctly.
  *
  * Checks:
  *   - mise binary is installed
  *   - mise doctor runs successfully
  *   - Tools from .tool-versions are available on PATH via shims
  */
object MiseVerifier {
  // 'mise' should be on the $PATH
  // replace with install location (`/home/vscode/.local/bin/mise`) for debugging
  private val miseBin = "mise"

  def verify(runner: DevcontainerRunner): Either[String, Unit] =
    for {
      _ <- checkMiseInstalled(runner)
      _ <- checkMiseShimsOnPathAfterActivation(runner)
      _ <- checkMiseDoctor(runner)
      _ <- checkMiseToolsAvailable(runner)
    } yield ()

  private def checkMiseInstalled(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec(s"$miseBin --version")
    if (result.succeeded) Right(())
    else Left(s"mise is not installed: ${result.combinedOutput}")
  }

  private val activateMise = """eval "$(mise activate --shims bash)" &>/dev/null"""
  private val activateMiseAndGetPath = activateMise + " && echo $PATH"
  private def checkMiseShimsOnPathAfterActivation(runner: DevcontainerRunner): Either[String, Unit] = {
    val pathResult            = runner.exec(activateMiseAndGetPath)
    val expectedShimsLocation = "/home/vscode/.local/share/mise/shims"
    val shimsLocationPresent  = pathResult.stdout.split(":").contains(expectedShimsLocation)
    if (shimsLocationPresent) Right(())
    else Left(s"$expectedShimsLocation not found in path $pathResult")
  }

  private val activateMiseAndRunDoctor = activateMise + s" && $miseBin doctor"
  private def checkMiseDoctor(runner: DevcontainerRunner): Either[String, Unit] = {
    // mise doctor may return non-zero for warnings, so we just check it runs
    // and produces output containing expected sections
    val result = runner.exec(activateMiseAndRunDoctor)
    if (result.stdout.contains("toolset:") || result.stdout.contains("dirs:")) {
      Right(())
    } else {
      Left(s"mise doctor did not produce expected output: ${result.combinedOutput}")
    }
  }

  private val activateMiseAndGetNodeVersion = activateMise + s" && node --version"
  private def checkMiseToolsAvailable(runner: DevcontainerRunner): Either[String, Unit] = {
    // Check that node is available via mise shims (our test fixture installs node 24)
    val result = runner.exec(activateMiseAndGetNodeVersion)
    if (result.succeeded && result.stdout.contains("v24")) {
      Right(())
    } else if (result.succeeded) {
      Left(s"node is available but wrong version: ${result.stdout}")
    } else {
      Left(s"node is not available via mise shims: ${result.combinedOutput}")
    }
  }
}
