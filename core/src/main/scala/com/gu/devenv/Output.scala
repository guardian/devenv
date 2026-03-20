package com.gu.devenv

import com.gu.devenv.Filesystem.FileSystemStatus
import fansi.*

object Output {

  /** Formatting conventions:
    *   - Filenames/paths: Cyan
    *   - Commands: Bold Cyan
    *   - Status: Green (success), Light Gray (neutral), Red (error), Yellow (warning)
    *   - Code snippets: Bold Green
    *   - Section headings: Bold White
    *   - Warning/Error headings: Bold Yellow / Bold Red
    *   - Dividers: Light Blue
    */

  // Public API

  def initResultMessage(result: InitResult): String = {
    val table     = buildInitTable(result)
    val nextSteps = buildInitNextSteps()

    s"""$table
       |
       |$nextSteps""".stripMargin
  }

  def generateResultMessage(result: GenerateResult): String =
    result match {
      case GenerateResult.Success(userStatus, sharedStatus) =>
        val table     = buildGenerateTable(userStatus, sharedStatus)
        val nextSteps = buildGenerateNextSteps()
        s"""$table
           |
           |$nextSteps""".stripMargin

      case GenerateResult.NotInitialized =>
        buildNotInitializedMessage()

      case GenerateResult.ConfigNotCustomized =>
        buildConfigNotCustomizedMessage()

      case GenerateResult.MismatchOnRemoteUserAndImage =>
        buildMismatchOnRemoteUserAndImage()
    }

  def checkResultMessage(result: CheckResult): String =
    result match {
      case CheckResult.Match(userPath, sharedPath) =>
        buildCheckMatchMessage(userPath, sharedPath)
      case CheckResult.Mismatch(userMismatch, sharedMismatch, userPath, sharedPath) =>
        buildCheckMismatchMessage(userMismatch, sharedMismatch, userPath, sharedPath)
      case CheckResult.NotInitialized =>
        buildNotInitializedMessage()
    }

  // Init message builders (called by initResultMessage)

  private def buildInitTable(result: InitResult): String = {
    val rows = List(
      (".devcontainer/", formatInitStatus(result.devcontainerStatus)),
      (".devcontainer/user/", formatInitStatus(result.userStatus)),
      (".devcontainer/shared/", formatInitStatus(result.sharedStatus)),
      (
        ".devcontainer/.gitignore",
        formatGitignoreStatus(result.gitignoreStatus)
      ),
      (".devcontainer/devenv.yaml", formatInitStatus(result.devenvStatus))
    )
    buildTable("Initialization Summary:", rows, 32)
  }

  private def buildInitNextSteps(): String =
    s"""${Bold.On("Next steps:")}
       |  1. Edit ${Color.Cyan(".devcontainer/devenv.yaml")} to configure your project
       |  2. Run ${Bold.On(
        Color.Cyan("devenv generate")
      )} to create devcontainer files""".stripMargin

  // Generate message builders (called by generateResultMessage)

  private def buildGenerateTable(
      userDevcontainerStatus: FileSystemStatus,
      sharedDevcontainerStatus: FileSystemStatus
  ): String = {
    val rows = List(
      (
        ".devcontainer/user/devcontainer.json",
        formatGenerateStatus(userDevcontainerStatus)
      ),
      (
        ".devcontainer/shared/devcontainer.json",
        formatGenerateStatus(sharedDevcontainerStatus)
      )
    )

    buildTable("Generation Summary:", rows, 47)
  }

  private def buildNotInitializedMessage(): String = {
    val header  = Bold.On(Color.Red("Project not initialized"))
    val divider = Color.Red("━" * 60)

    s"""$header
       |$divider
       |${Color.Yellow("The .devcontainer directory has not been initialized.")}
       |
       |Please complete these steps:
       |  1. Run ${Bold.On(Color.Cyan("devenv init"))} to set up the project structure
       |  2. Edit ${Color.Cyan(".devcontainer/devenv.yaml")} to configure your project
       |  3. Run ${Bold.On(
        Color.Cyan("devenv generate")
      )} again to create devcontainer files""".stripMargin
  }

  private def buildConfigNotCustomizedMessage(): String = {
    val header  = Bold.On(Color.Yellow("Configuration not customized"))
    val divider = Color.Yellow("━" * 60)

    s"""$header
       |$divider
       |${Color.Yellow(
        "The devenv.yaml configuration file still contains the placeholder project name."
      )}
       |
       |Please edit ${Color.Cyan(".devcontainer/devenv.yaml")} and change:
       |  ${Bold.On(Color.Red("name: \"CHANGE_ME\""))}
       |to:
       |  ${Bold.On(Color.Green("name: \"Your Project Name\""))}
       |
       |Then run ${Bold.On(Color.Cyan("devenv generate"))} again.""".stripMargin
  }

  private def buildMismatchOnRemoteUserAndImage(): String = {
    val header  = Bold.On(Color.Yellow("Configuration not customized"))
    val divider = Color.Yellow("━" * 60)

    s"""$header
       |$divider
       |${Color.Yellow(
        "The devenv.yaml configuration file does not override the default base image but has a different remoteUser."
      )}
       |
       |Please edit ${Color.Cyan(".devcontainer/devenv.yaml")} and either remove the :
       |  ${Bold.On(Color.Red("\"remoteUser\": <user>"))}
       |setting or change to a matching:
       |  ${Bold.On(Color.Green("\"image\": <image>"))}
       |
       |Then run ${Bold.On(Color.Cyan("devenv generate"))} again.""".stripMargin
  }

  private def buildGenerateNextSteps(): String =
    s"""${Bold.On("You can now:")}
       |  • Open the project in your IDE and reopen in container
       |  • Use the shared config for cloud-based development""".stripMargin

  // Check message builders (called by checkResultMessage)

  private def buildCheckMatchMessage(maybeUserPath: Option[String], sharedPath: String): String = {
    val header  = Bold.On(Color.Green("✓ Configuration is up-to-date"))
    val divider = Color.Green("━" * 60)

    val (userFileLine, status) = maybeUserPath match {
      case Some(userFilePath) =>
        (
          s"  ✓ ${Color.Cyan(userFilePath)}",
          Color.Green("All devcontainer files match the current configuration.")
        )
      case None =>
        val line = s"  ⊘ ${Color.LightGray("user configuration skipped")}"
        val status =
          s"""|${Color.Green("devcontainer files match the current configuration.")}
              |
              |${Color.LightGray("Note:")}
              |  The user devcontainer file is missing but was not checked
              |  because there is no user configuration.
              |
              |  This is normal in CI checks, and locally if you have not
              |  added any personal configuration options.""".stripMargin
        (line, status)
    }

    s"""$header
       |$divider
       |$status
       |
       |Files checked:
       |$userFileLine
       |  ✓ ${Color.Cyan(sharedPath)}""".stripMargin
  }

  private def buildCheckMismatchMessage(
      userMismatch: Option[FileDiff],
      sharedMismatch: Option[FileDiff],
      userPath: String,
      sharedPath: String
  ): String = {
    val header  = Bold.On(Color.Red("✗ Configuration is out-of-date"))
    val divider = Color.Red("━" * 60)

    val mismatchedFiles = List(
      userMismatch.map(diff => s"  ✗ ${Color.Cyan(diff.path)}"),
      sharedMismatch.map(diff => s"  ✗ ${Color.Cyan(diff.path)}")
    ).flatten.mkString("\n")

    val matchedFiles = List(
      if (userMismatch.isEmpty) Some(s"  ✓ ${Color.Cyan(userPath)}") else None,
      if (sharedMismatch.isEmpty) Some(s"  ✓ ${Color.Cyan(sharedPath)}") else None
    ).flatten.mkString("\n")

    val filesSection = if (matchedFiles.nonEmpty) {
      s"""Files out-of-date:
         |$mismatchedFiles
         |
         |Files up-to-date:
         |$matchedFiles""".stripMargin
    } else {
      s"""Files out-of-date:
         |$mismatchedFiles""".stripMargin
    }

    s"""$header
       |$divider
       |${Color.Yellow("The devcontainer files do not match the current configuration.")}
       |
       |$filesSection
       |
       |Run ${Bold.On(
        Color.Cyan("devenv generate")
      )} to update the devcontainer files.""".stripMargin
  }

  // Shared table builder (called by buildInitTable and buildGenerateTable)

  private def buildTable(
      title: String,
      rows: List[(String, (String, String, Str => Str))],
      pathPadding: Int
  ): String = {
    val header  = Bold.On(title)
    val divider = Color.LightBlue("━" * 60)

    val tableRows = rows
      .map { case (path, (emoji, text, colorFn)) =>
        val paddedPath = path.padTo(pathPadding, ' ')
        s"  $emoji ${Color.Cyan(paddedPath)} ${colorFn(text)}"
      }
      .mkString("\n")

    s"""$header
       |$divider
       |$tableRows
       |$divider""".stripMargin
  }

  // Status formatters (low-level helpers called by table builders)

  private def formatInitStatus(
      status: Filesystem.FileSystemStatus
  ): (String, String, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created =>
        ("✅", "Created", s => Color.Green(s))
      case FileSystemStatus.AlreadyExists =>
        ("⚪", "Already exists", s => Color.LightGray(s))
    }
  }

  private def formatGitignoreStatus(
      status: Filesystem.GitignoreStatus
  ): (String, String, Str => Str) = {
    import Filesystem.GitignoreStatus
    status match {
      case GitignoreStatus.Created =>
        ("✅", "Created", s => Color.Green(s))
      case GitignoreStatus.AlreadyExistsWithExclusion =>
        ("⚪", "Already exists", s => Color.LightGray(s))
      case GitignoreStatus.Updated =>
        ("🔄", "Updated", s => Color.Green(s))
    }
  }

  private def formatGenerateStatus(
      status: Filesystem.FileSystemStatus
  ): (String, String, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created =>
        ("✅", "Created", s => Color.Green(s))
      case FileSystemStatus.AlreadyExists =>
        ("🔄", "Updated", s => Color.Green(s))
    }
  }
}
