package com.gu.devenv.docker.testutils

import java.nio.file.Path

/** Runs devcontainer CLI commands and manages container lifecycle.
  *
  * Uses npx to run @devcontainers/cli, which will automatically download and cache the package if
  * needed.
  *
  * Note: devenv generates devcontainer.json files at .devcontainer/shared/devcontainer.json and
  * .devcontainer/user/devcontainer.json, so we use --config to point to the shared one.
  */
class DevcontainerRunner(workspaceDir: Path) {
  private val workspacePath = workspaceDir.toAbsolutePath.toString
  private val configPath =
    workspaceDir.resolve(".devcontainer/shared/devcontainer.json").toAbsolutePath.toString
  private val devcontainer = "npx --yes @devcontainers/cli"

  // Disable Git root mounting since test workspaces are temporary directories without Git.
  // Without this, the devcontainer CLI defaults to --mount-workspace-git-root=true which
  // causes incomplete workspace mounting on CI environments (e.g., GHA).
  private val noGitRoot = "--mount-workspace-git-root=false"

  /** Builds the devcontainer image */
  def build(): CommandResult =
    CommandRunner.run(s"$devcontainer build --workspace-folder $workspacePath --config $configPath")

  /** Starts the devcontainer and returns its ID */
  def up(): Either[String, Unit] = {
    val result = CommandRunner.run(
      s"$devcontainer up --workspace-folder $workspacePath --config $configPath $noGitRoot"
    )
    if (result.succeeded) {
      Right(())
    } else {
      Left(s"Failed to start container: ${result.combinedOutput}")
    }
  }

  /** Executes a command inside the running devcontainer
    *
    * Note that the command cannot contain single quotes
    */
  def exec(command: String): CommandResult =
    CommandRunner.run(
      s"""$devcontainer exec --workspace-folder $workspacePath --config $configPath $noGitRoot -- bash -c '$command'"""
    )

  /** Stops and removes the devcontainer
    *
    * TODO: parameterize the mise data volume, use isolated volumes in test, clean those up here
    */
  def down(): CommandResult = {
    // The devcontainer CLI doesn't have a down command, so we find and stop the container
    val findResult = CommandRunner.run(
      s"""docker ps -q --filter "label=devcontainer.local_folder=$workspacePath""""
    )
    if (findResult.succeeded && findResult.stdout.trim.nonEmpty) {
      val containerId = findResult.stdout.trim
      CommandRunner.run(s"docker rm -f $containerId")
    } else {
      // Container might not exist so this isn't necessarily a failure
      println(s"Warning: Could not find container to stop for workspace: $workspacePath")
      CommandResult(0, "", "")
    }
  }

}
