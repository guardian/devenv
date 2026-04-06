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
      _ <- checkMiseDoctor(runner)
      _ <- checkMiseToolsAvailable(runner)
    } yield ()

  private def checkMiseInstalled(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec(s"$miseBin --version")
    if (result.succeeded) Right(())
    else Left(s"mise is not installed: ${result.combinedOutput}")
  }

  private def checkMiseDoctor(runner: DevcontainerRunner): Either[String, Unit] = {
    // mise doctor may return non-zero for warnings, so we just check it runs
    // and produces output containing expected sections
    val result = runner.exec(s"$miseBin doctor")
    if (result.stdout.contains("toolset:") || result.stdout.contains("dirs:")) {
      Right(())
    } else {
      Left(s"mise doctor did not produce expected output: ${result.combinedOutput}")
    }
  }

  private def checkMiseToolsAvailable(runner: DevcontainerRunner): Either[String, Unit] = {
    // Check that node is available via mise shims (our test fixture installs node 24)
    // In normal usage, the activation line is in ~/.bashrc
    val result = runner.exec("eval \"$(mise activate bash)\" && node --version")
    if (result.succeeded && result.stdout.contains("v24")) {
      Right(())
    } else if (result.succeeded) {
      Left(s"node is available but wrong version: ${result.stdout}")
    } else {
      Left(s"node is not available via mise shims: ${result.combinedOutput}")
    }
  }
}
