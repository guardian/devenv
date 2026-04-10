package com.gu.devenv

import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.given
import scala.util.Try
import com.gu.devenv.modules.Modules.Module

object Filesystem {
  val PLACEHOLDER_PROJECT_NAME = "CHANGE_ME"

  def resolveDevenvPaths(devcontainerDir: Path): DevEnvPaths = {
    val userDir   = devcontainerDir.resolve("user")
    val sharedDir = devcontainerDir.resolve("shared")
    DevEnvPaths(
      devcontainerDir = devcontainerDir,
      userDir = userDir,
      userDevcontainerFile = userDir.resolve("devcontainer.json"),
      sharedDir = sharedDir,
      sharedDevcontainerFile = sharedDir.resolve("devcontainer.json"),
      gitignoreFile = devcontainerDir.resolve(".gitignore"),
      devenvFile = devcontainerDir.resolve("devenv.yaml"),
      escapeHatch = devcontainerDir.resolve("escapehatch.json")
    )
  }

  def resolveUserConfigPaths(root: Path): UserConfigPaths =
    UserConfigPaths(
      devenvConf = root.resolve("devenv.yaml")
    )

  def createDirIfNotExists(dir: Path): Try[FileSystemStatus] = Try {
    if (!Files.exists(dir)) {
      Files.createDirectory(dir)
      FileSystemStatus.Created
    } else {
      FileSystemStatus.AlreadyExists
    }
  }

  def readFile(path: Path): Try[String] = Try {
    val bytes = Files.readAllBytes(path)
    new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
  }

  /** Updates a file by overwriting its content, or creates it if it doesn't exist.
    *
    * Use this for files that should be regenerated on each run (e.g., devcontainer.json). Do NOT
    * use this for init behavior where existing files should be preserved.
    */
  def updateFile(path: Path, content: String): Try[FileSystemStatus] = Try {
    val exists = Files.exists(path)
    // Create parent directories if they don't exist
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(
      path,
      content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
    if (exists) FileSystemStatus.AlreadyExists else FileSystemStatus.Created
  }

  def setupGitignore(gitignoreFile: Path): Try[GitignoreStatus] = Try {
    if (!Files.exists(gitignoreFile)) {
      Files.write(
        gitignoreFile,
        gitignoreContents.getBytes,
        StandardOpenOption.CREATE_NEW
      )
      GitignoreStatus.Created
    } else {
      // check if the .gitignore file already includes the `user/` entry we want to add
      val hasUserExclusion = Files
        .lines(gitignoreFile)
        .iterator()
        .asScala
        .map(_.trim)
        .contains("user/")

      if (hasUserExclusion) {
        GitignoreStatus.AlreadyExistsWithExclusion
      } else {
        // Append our comment and user/ entry to the existing file
        Files.write(
          gitignoreFile,
          gitignoreContents.getBytes,
          StandardOpenOption.APPEND
        )
        GitignoreStatus.Updated
      }
    }
  }

  def setupDevenv(devenvFile: Path, modules: List[Module]): Try[FileSystemStatus] = Try {
    if (!Files.exists(devenvFile)) {
      Files.write(
        devenvFile,
        devenvContents(modules).getBytes,
        StandardOpenOption.CREATE_NEW
      )
      FileSystemStatus.Created
    } else {
      FileSystemStatus.AlreadyExists
    }
  }

  private val gitignoreContents =
    """|# User-specific devcontainer directory with merged personal preferences
       |user/
       |""".stripMargin

  private def devenvContents(modules: List[Module]) = {
    val moduleDescriptions = modules
      .map { module =>
        s"# - ${module.name}: ${module.summary}"
      }
      .mkString("\n")
    val stubModules = modules
      .map { module =>
        if (module.enabledByDefault)
          s"  - ${module.name}"
        else
          s"  # - ${module.name}  # (disabled by default)"
      }
      .mkString("\n")
    s"""|# Devenv project configuration
        |# Edit this file to configure your project's devcontainer
        |# and then run `devenv generate` to create the devcontainer.json files
        |
        |# REQUIRED: Change this to your project name
        |name: "CHANGE_ME"
        |
        |# Modules: Built-in functionality
        |$moduleDescriptions
        |# To disable, comment out or remove items from this list
        |modules:
        |$stubModules
        |
        |# Optional: Ports to forward
        |# forwardPorts:
        |#   - 8080  # same port on host and container
        |#   - "8000:9000"  # hostPort:containerPort
        |
        |# Optional: Mount directories from host to container
        |# mounts:
        |#   - "source=$${localEnv:HOME}/.gu/example,target=/home/vscode/.gu/example,readonly,type=bind,consistency=cached"
        |
        |# Optional: IDE plugins - shared project plugins like language support
        |# plugins:
        |#   vscode: []
        |#   intellij: []
        |
        |# Optional: Commands to run after container creation
        |# Each command has a 'cmd' field and a 'workingDirectory' field
        |# e.g.
        |# postCreateCommand:
        |#   - cmd: "npm install"
        |#     workingDirectory: "."
        |#   - cmd: "make setup"
        |#     workingDirectory: "/workspaces/project"
        |# postStartCommand:
        |#   - cmd: "echo 'Container started successfully'"
        |#     workingDirectory: "."
        |""".stripMargin
  }

  enum FileSystemStatus {
    case Created
    case AlreadyExists
  }

  enum GitignoreStatus {
    case Created
    case AlreadyExistsWithExclusion
    case Updated
  }
}
