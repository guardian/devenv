package com.gu.devenv

import com.gu.devenv.Filesystem.FileSystemStatus
import com.gu.devenv.OutputFormatter.styled
import com.gu.devenv.modules.Modules.ModuleResolutionError
import fansi.Str

object Output {
  // Public API

  def initResultMessage(result: InitResult)(using formatter: OutputFormatter): Str = {
    val table     = buildInitTable(result)
    val nextSteps = buildInitNextSteps()

    styled"""$table
            |
            |$nextSteps"""
  }

  def generateResultMessage(result: GenerateResult)(using formatter: OutputFormatter): Str =
    result match {
      case GenerateResult.Success(userStatus, sharedStatus) =>
        val table     = buildGenerateTable(userStatus, sharedStatus)
        val nextSteps = buildGenerateNextSteps()
        styled"""$table
                |
                |$nextSteps"""

      case GenerateResult.NotInitialized =>
        buildNotInitializedMessage()

      case GenerateResult.ConfigNotCustomized =>
        buildConfigNotCustomizedMessage()

      case GenerateResult.InvalidModules(error) =>
        buildInvalidModulesMessage(error)
    }

  def checkResultMessage(result: CheckResult)(using formatter: OutputFormatter): Str =
    result match {
      case CheckResult.Match(userPath, sharedPath) =>
        buildCheckMatchMessage(userPath, sharedPath)
      case CheckResult.Mismatch(userMismatch, sharedMismatch, userPath, sharedPath) =>
        buildCheckMismatchMessage(userMismatch, sharedMismatch, userPath, sharedPath)
      case CheckResult.NotInitialized =>
        buildNotInitializedMessage()
      case CheckResult.InvalidModules(error) =>
        buildInvalidModulesMessage(error)
    }

  // Init message builders (called by initResultMessage)

  private def buildInitTable(result: InitResult)(using OutputFormatter): Str = {
    val rows = List(
      (".devcontainer/", formatInitStatus(result.devcontainerStatus)),
      (".devcontainer/user/", formatInitStatus(result.userStatus)),
      (".devcontainer/shared/", formatInitStatus(result.sharedStatus)),
      (
        ".devcontainer/.gitignore",
        formatGitignoreStatus(result.gitignoreStatus)
      ),
      (".devcontainer/devenv.yaml", formatInitStatus(result.devenvStatus)),
      (".devcontainer/README.md", formatInitStatus(result.readmeStatus))
    )
    buildTable("Initialization Summary:", rows, 32)
  }

  private def buildInitNextSteps()(using formatter: OutputFormatter): Str =
    styled"""${formatter.sectionHeading("Next steps:")}
            |  1. Edit ${formatter.filename(".devcontainer/devenv.yaml")} to configure your project
            |  2. Run ${formatter.command("devenv generate")} to create devcontainer files"""

  // Generate message builders (called by generateResultMessage)

  private def buildGenerateTable(
      userDevcontainerStatus: FileSystemStatus,
      sharedDevcontainerStatus: FileSystemStatus
  )(using OutputFormatter): Str = {
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

  private def buildNotInitializedMessage()(using formatter: OutputFormatter): Str = {
    val header          = formatter.errorHeading("Project not initialized")
    val divider         = formatter.errorDivider("━" * 60)
    val generateCommand = formatter.command("devenv generate")

    styled"""$header
            |$divider
            |${formatter.warning("The .devcontainer directory has not been initialized.")}
            |
            |Please complete these steps:
            |  1. Run ${formatter.command("devenv init")} to set up the project structure
            |  2. Edit ${formatter.filename(".devcontainer/devenv.yaml")} to configure your project
            |  3. Run $generateCommand again to create devcontainer files"""
  }

  private def buildConfigNotCustomizedMessage()(using formatter: OutputFormatter): Str = {
    val header  = formatter.warningHeading("Configuration not customized")
    val divider = formatter.warningDivider("━" * 60)
    val warning =
      formatter.warning(
        "The devenv.yaml configuration file still contains the placeholder project name."
      )

    styled"""$header
            |$divider
            |$warning
            |
            |Please edit ${formatter.filename(".devcontainer/devenv.yaml")} and change:
            |  ${formatter.invalidCode("name: \"CHANGE_ME\"")}
            |to:
            |  ${formatter.validCode("name: \"Your Project Name\"")}
            |
            |Then run ${formatter.command("devenv generate")} again."""
  }

  private def buildInvalidModulesMessage(
      error: ModuleResolutionError
  )(using formatter: OutputFormatter): Str = {
    val header       = formatter.errorHeading("Invalid module configuration")
    val divider      = formatter.errorDivider("━" * 60)
    val configPath   = formatter.filename(".devcontainer/devenv.yaml")
    val errorMessage = error match {
      case ModuleResolutionError.UnknownModule(name) =>
        s"Unknown module: '$name'"
      case ModuleResolutionError.UnknownDependency(module, dependency) =>
        s"Module '$module' depends on unknown module '$dependency'"
      case ModuleResolutionError.DependencyNotEnabled(module, dependency) =>
        s"Module '$module' depends on '$dependency', but it is not enabled in the project"
      case ModuleResolutionError.DependencyCycle(modules) =>
        s"A dependency cycle was detected among modules: ${modules.mkString(", ")}"

    }

    styled"""$header
            |$divider
            |${formatter.warning(errorMessage)}
            |
            |Please update the ${formatter.filename(
        "modules"
      )} list in $configPath and try again."""
  }

  private def buildGenerateNextSteps()(using formatter: OutputFormatter): Str =
    styled"""${formatter.sectionHeading("You can now:")}
            |  • Open the project in your IDE and reopen in container
            |  • Use the shared config for cloud-based development"""

  // Check message builders (called by checkResultMessage)

  private def buildCheckMatchMessage(
      maybeUserPath: Option[String],
      sharedPath: String
  )(using formatter: OutputFormatter): Str = {
    val header  = formatter.successHeading("✓ Configuration is up-to-date")
    val divider = formatter.successDivider("━" * 60)

    val (userFileLine, status) = maybeUserPath match {
      case Some(userFilePath) =>
        (
          styled"  ✓ ${formatter.filename(userFilePath)}",
          formatter.success("All devcontainer files match the current configuration.")
        )
      case None =>
        val line   = styled"  ⊘ ${formatter.neutral("user configuration skipped")}"
        val status =
          styled"""|${formatter.success("devcontainer files match the current configuration.")}
                  |
                  |${formatter.neutral("Note:")}
                  |  The user devcontainer file is missing but was not checked
                  |  because there is no user configuration.
                  |
                  |  This is normal in CI checks, and locally if you have not
                  |  added any personal configuration options."""
        (line, status)
    }

    styled"""$header
            |$divider
            |$status
            |
            |Files checked:
            |$userFileLine
            |  ✓ ${formatter.filename(sharedPath)}"""
  }

  private def buildCheckMismatchMessage(
      userMismatch: Option[FileDiff],
      sharedMismatch: Option[FileDiff],
      userPath: String,
      sharedPath: String
  )(using formatter: OutputFormatter): Str = {
    val header  = formatter.errorHeading("✗ Configuration is out-of-date")
    val divider = formatter.errorDivider("━" * 60)

    val mismatchedFiles = List(
      userMismatch.map(diff => styled"  ✗ ${formatter.filename(diff.path)}"),
      sharedMismatch.map(diff => styled"  ✗ ${formatter.filename(diff.path)}")
    ).flatten
    val mismatchedFilesOutput = Str.join(mismatchedFiles, Str("\n"))

    val matchedFiles = List(
      if (userMismatch.isEmpty) Some(styled"  ✓ ${formatter.filename(userPath)}") else None,
      if (sharedMismatch.isEmpty) Some(styled"  ✓ ${formatter.filename(sharedPath)}") else None
    ).flatten
    val matchedFilesOutput = Str.join(matchedFiles, Str("\n"))

    val filesSection = if (matchedFiles.nonEmpty) {
      styled"""Files out-of-date:
              |$mismatchedFilesOutput
              |
              |Files up-to-date:
              |$matchedFilesOutput"""
    } else {
      styled"""Files out-of-date:
              |$mismatchedFilesOutput"""
    }

    styled"""$header
            |$divider
            |${formatter.warning("The devcontainer files do not match the current configuration.")}
            |
            |$filesSection
            |
            |Run ${formatter.command("devenv generate")} to update the devcontainer files."""
  }

  // Shared table builder (called by buildInitTable and buildGenerateTable)

  private def buildTable(
      title: String,
      rows: List[(String, (String, Str, Str => Str))],
      pathPadding: Int
  )(using formatter: OutputFormatter): Str = {
    val header  = formatter.sectionHeading(title)
    val divider = formatter.sectionDivider("━" * 60)

    val tableRows = rows
      .map { case (path, (emoji, text, colorFn)) =>
        val paddedPath = path.padTo(pathPadding, ' ')
        styled"  ${Str(emoji)} ${formatter.filename(paddedPath)} ${colorFn(text)}"
      }
    val tableRowsOutput = Str.join(tableRows, Str("\n"))

    styled"""$header
            |$divider
            |$tableRowsOutput
            |$divider"""
  }

  // Status formatters (low-level helpers called by table builders)

  private def formatInitStatus(
      status: Filesystem.FileSystemStatus
  )(using formatter: OutputFormatter): (String, Str, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created =>
        ("✅", "Created", formatter.success)
      case FileSystemStatus.AlreadyExists =>
        ("⚪", "Already exists", formatter.neutral)
    }
  }

  private def formatGitignoreStatus(
      status: Filesystem.GitignoreStatus
  )(using formatter: OutputFormatter): (String, Str, Str => Str) = {
    import Filesystem.GitignoreStatus
    status match {
      case GitignoreStatus.Created =>
        ("✅", "Created", formatter.success)
      case GitignoreStatus.AlreadyExistsWithExclusion =>
        ("⚪", "Already exists", formatter.neutral)
      case GitignoreStatus.Updated =>
        ("🔄", "Updated", formatter.success)
    }
  }

  private def formatGenerateStatus(
      status: Filesystem.FileSystemStatus
  )(using formatter: OutputFormatter): (String, Str, Str => Str) = {
    import Filesystem.FileSystemStatus
    status match {
      case FileSystemStatus.Created =>
        ("✅", "Created", formatter.success)
      case FileSystemStatus.AlreadyExists =>
        ("🔄", "Updated", formatter.success)
    }
  }
}
