package com.gu.devenv.docker.verifiers

import com.gu.devenv.docker.testutils.DevcontainerRunner

/** Verifies that setup reported its completion.
  *
  * Checks:
  *   - the postCreate log contains the setup complete message
  */
object SetupCompletionVerifier {

  private val postCreateLog = "/var/log/post-create.log"

  def verify(runner: DevcontainerRunner): Either[String, Unit] = {
    val result = runner.exec(s"""grep -q "Setup complete" $postCreateLog""")
    if (result.succeeded) Right(())
    else Left(s"setup completion was not reported: ${result.combinedOutput}")
  }
}
