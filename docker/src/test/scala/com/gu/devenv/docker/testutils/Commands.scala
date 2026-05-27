package com.gu.devenv.docker.testutils

import scala.sys.process.*

object CommandRunner {

  /** Runs a shell command and captures stdout, stderr, and exit code */
  def run(command: String): CommandResult = {
    val stdout        = new StringBuilder
    val stderr        = new StringBuilder
    val processLogger = ProcessLogger(
      line => stdout.append(line).append("\n"): Unit,
      line => stderr.append(line).append("\n"): Unit
    )
    val exitCode = command.!(processLogger)
    CommandResult(exitCode, stdout.toString.trim, stderr.toString.trim)
  }
}

/** Result of running a command, either on the host or inside a container */
case class CommandResult(exitCode: Int, stdout: String, stderr: String) {
  def succeeded: Boolean = exitCode == 0
  def failed: Boolean    = !succeeded

  /** Combines stdout and stderr for logging */
  def combinedOutput: String =
    (if (stdout.nonEmpty) s"stdout:\n$stdout" else "") +
      (if (stderr.nonEmpty) s"\nstderr:\n$stderr" else "")
}
