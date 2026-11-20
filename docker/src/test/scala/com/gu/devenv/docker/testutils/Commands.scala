package com.gu.devenv.docker.testutils

import scala.sys.process.*

object CommandRunner {

  private def onPath(executable: String): Boolean =
    sys.env
      .getOrElse("PATH", "")
      .split(java.io.File.pathSeparator)
      .exists(dir => java.io.File(dir, executable).canExecute)

  /** sbt may be started without mise's tools on the PATH (e.g. from an IDE), so resolve tools such
    * as npx via mise when they are not directly available.
    */
  private def withTools(command: String): String =
    if (command.startsWith("npx ") && !onPath("npx") && onPath("mise")) s"mise exec -- $command"
    else command

  /** Runs a shell command and captures stdout, stderr, and exit code */
  def run(rawCommand: String): CommandResult = {
    val command       = withTools(rawCommand)
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
