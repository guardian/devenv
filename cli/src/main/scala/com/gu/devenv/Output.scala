package com.gu.devenv

import com.gu.devenv.Filesystem.FileSystemStatus
import com.gu.devenv.OutputFormatter.styled
import com.gu.devenv.Releases.UpdateCheckResult
import com.gu.devenv.modules.Modules.ModuleResolutionError
import fansi.Str

import scala.util.{Failure, Success, Try}

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

  def usageMessage(
      release: String,
      architecture: Option[String],
      branch: Option[String]
  )(using formatter: OutputFormatter): Str = {
    val releaseLine = Str(s"  release   $release")
    val archLine    = architecture.map(a => Str(s"  arch      $a"))
    val branchLine  = branch.map(b => Str(s"  branch    $b"))
    val devModeNote =
      if (architecture.isEmpty && branch.isEmpty)
        Some(Str("  (running in development mode)"))
      else
        None
    val versionInfo =
      Str.join(List(Some(releaseLine), archLine, branchLine, devModeNote).flatten, Str("\n"))

    // fmt: off
    styled"""${formatter.sectionHeading("Usage:")} devenv <command> [--help]
            |
            |A CLI tool for managing devcontainer configurations for your projects.
            |Generates user-specific and shared devcontainer.json files from
            |devenv.yaml configuration files, separating team-wide project settings
            |from personal user preferences.
            |
            |${formatter.sectionHeading(
        "Important:"
      )} Run all commands from the root of your project.
            |The .devcontainer configuration is created and updated in the current
            |working directory so running elsewhere will place files in the wrong
            |location.
            |
            |Run 'devenv <command> --help' (or -h or help) for help on any command.
            |
            |${formatter.sectionHeading("Commands:")}
            |
            |  ${formatter.command("init")}
            |    Initialises the .devcontainer directory structure for a project.
            |    Run this once from the root of a repository before using 'generate'.
            |    Reads:   nothing (uses built-in defaults)
            |    Writes:  .devcontainer/devenv.yaml        (project config template)
            |             .devcontainer/.gitignore         (excludes user/ directory)
            |             .devcontainer/README.md          (usage guidance)
            |             .devcontainer/shared/            (directory, populated by 'generate')
            |             .devcontainer/user/              (directory, populated by 'generate')
            |
            |  ${formatter.command("generate")}
            |    Generates devcontainer.json files from the current devenv configuration.
            |    Run this after editing devenv.yaml to apply your changes.
            |    Reads:   .devcontainer/devenv.yaml        (project config, required)
            |             ~/.config/devenv/devenv.yaml     (user config, optional)
            |    Writes:  .devcontainer/shared/devcontainer.json  (project-only, check in)
            |             .devcontainer/user/devcontainer.json    (merged with user prefs)
            |
            |  ${formatter.command("check")}
            |    Verifies that the saved devcontainer.json files match what 'generate'
            |    would produce from the current configuration. Exits non-zero if they
            |    differ. Use in CI to ensure configs are not stale.
            |    Reads:   .devcontainer/devenv.yaml
            |             ~/.config/devenv/devenv.yaml     (user config, optional)
            |             .devcontainer/shared/devcontainer.json
            |             .devcontainer/user/devcontainer.json
            |    Writes:  nothing
            |
            |  ${formatter.command("version")}
            |    Prints the current devenv release version, architecture, and branch.
            |    Aliases: --version, -v
            |
            |  ${formatter.command("update")}
            |    Checks GitHub releases for a newer version of devenv and prints
            |    download instructions if one is available.
            |
            |  ${formatter.command("help")}
            |    Prints this help text.
            |    Aliases: --help, -h
            |    Any command also accepts --help/-h as a second argument.
            |
            |${formatter.sectionHeading("Configuration:")}
            |  Project config:  .devcontainer/devenv.yaml
            |    Project-specific settings (name, ports, IDE plugins, commands, modules).
            |    Checked into version control.
            |
            |  User config:     ~/.config/devenv/devenv.yaml
            |    Personal preferences (dotfiles, additional IDE plugins).
            |    Merged with project config for the user-specific devcontainer.
            |
            |${formatter.sectionHeading("Typical workflow:")}
            |  1. devenv init       -- create initial config file
            |  2. edit .devcontainer/devenv.yaml
            |  3. devenv generate   -- produce devcontainer.json files
            |  4. devenv check      -- verify in CI that files are up to date
            |
            |${formatter.sectionHeading("Version:")}
            |$versionInfo
            |"""
    // fmt: on
  }

  def versionMessage(
      release: String,
      architecture: Option[String],
      branch: Option[String]
  )(using formatter: OutputFormatter): Str = {
    val architectureStr = architecture.fold("")(arch => s" ($arch)")
    val branchStr       = branch.fold("")(branch => s" [$branch]")

    styled"${formatter.emphasis(release)}${Str(architectureStr)}${Str(branchStr)}"
  }

  def unknownCommandMessage(name: String)(using formatter: OutputFormatter): Str =
    formatter.error(s"Unknown command: $name")

  def updateCheckResultMessage(
      result: Try[UpdateCheckResult],
      currentVersion: String,
      architecture: Option[String]
  )(using formatter: OutputFormatter): Str =
    result match {
      case Success(UpdateCheckResult.UpToDate) =>
        val header  = formatter.successHeading("✅ Up-to-date")
        val divider = formatter.successDivider("━" * 60)
        val message =
          formatter.success(
            styled"Devenv ${formatter.emphasis(currentVersion)} is the latest version."
          )
        styled"""$header
                |$divider
                |$message
                |"""

      case Success(UpdateCheckResult.DevMode(latestRelease)) =>
        val header  = formatter.warningHeading("✓ Development mode")
        val divider = formatter.warningDivider("━" * 60)
        val message =
          formatter.warning("Devenv is in development mode; cannot check for updates.")
        val latest =
          formatter.warning(
            styled"The latest released version is ${formatter.emphasis(latestRelease.tagName)}"
          )
        styled"""$header
                |$divider
                |$message
                |$latest
                |"""

      case Success(UpdateCheckResult.UpdateAvailable(newerRelease, asset)) =>
        val header   = formatter.warningHeading("⬆\uFE0F Update available")
        val divider  = formatter.warningDivider("━" * 60)
        val message  = formatter.warning("An update is available")
        val update   = styled"${Str(currentVersion)} → ${formatter.emphasis(newerRelease.tagName)}"
        val release  = formatter.link(newerRelease.htmlUrl)
        val download = formatter.link(asset.browserDownloadUrl)
        styled"""$header
                |$divider
                |$message
                |
                |  $update
                |
                |Release notes and installation instructions:
                |  $release
                |
                |Or download from:
                |  $download
                |"""

      case Success(UpdateCheckResult.NoCompatibleAsset(newerRelease)) =>
        val header  = formatter.errorHeading("❌ No compatible update")
        val divider = formatter.errorDivider("━" * 60)
        val notice  = formatter.warning("An update is available:")
        val update  = styled"${Str(currentVersion)} → ${formatter.emphasis(newerRelease.tagName)}"
        val detail  = formatter.error(
          styled"No compatible download was found for: ${formatter.emphasis(architecture.getOrElse("unknown"))}"
        )
        val release = formatter.link(newerRelease.htmlUrl)
        styled"""$header
                |$divider
                |$notice
                |
                |  $update
                |
                |$detail
                |
                |Release notes:
                |  $release
                |"""

      case Success(UpdateCheckResult.NoArchitectureInfo(newerRelease)) =>
        val header  = formatter.errorHeading("❔ Cannot verify compatibility")
        val divider = formatter.errorDivider("━" * 60)
        val notice  = formatter.warning("An update is available:")
        val update  = styled"${Str(currentVersion)} → ${formatter.emphasis(newerRelease.tagName)}"
        val detail  = formatter.warning(
          styled"""CPU architecture information for your current version is not available.
                  |Cannot check for a compatible download."""
        )
        val release = formatter.link(newerRelease.htmlUrl)
        styled"""$header
                |$divider
                |$notice
                |
                |  $update
                |
                |$detail
                |
                |Release notes:
                |  $release
                |"""

      case Failure(exception) =>
        val header  = formatter.errorHeading("❌ Update check failed")
        val divider = formatter.errorDivider("━" * 60)
        val message = formatter.error("An error occurred while checking for updates:")
        val error   = formatter.error(exception.getMessage)
        styled"""$header
                |$divider
                |$message
                |$error
                |"""
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
    val header  = formatter.errorHeading("Project not initialized")
    val divider = formatter.errorDivider("━" * 60)
    val warning = formatter.warning("The .devcontainer directory has not been initialized.")
    styled"""$header
            |$divider
            |$warning
            |
            |Please complete these steps:
            |  1. Run ${formatter.command("devenv init")} to set up the project structure
            |  2. Edit ${formatter.filename(".devcontainer/devenv.yaml")} to configure your project
            |  3. Run ${formatter.command("devenv generate")} again to create devcontainer files"""
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
        formatter.warning(s"Unknown module: '$name'")
      case ModuleResolutionError.UnknownDependency(module, dependency) =>
        formatter.warning(s"Module '$module' depends on unknown module '$dependency'")
      case ModuleResolutionError.DependencyNotEnabled(module, dependency) =>
        formatter.warning(
          s"Module '$module' depends on '$dependency', but it is not enabled in the project"
        )
      case ModuleResolutionError.DependencyCycle(modules) =>
        formatter.warning(
          s"A dependency cycle was detected among modules: ${modules.mkString(", ")}"
        )

    }

    styled"""$header
            |$divider
            |$errorMessage
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
