package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Plugins}

import scala.util.Try

/** Installs GitHub Copilot in the development container.
  *
  * We set up IDE integration and install the GitHub Copilot CLI, to provide Copilot integration in
  * our devcontainers.
  *
  * The CLI is a standalone tool, but also serves as the harness for GitHub Copilot in the IDE.
  *
  * See:
  *   - https://github.com/features/copilot
  *   - https://github.com/features/copilot/cli
  *
  * We expect this to be very widely required, so installation is handled via a module that is on by
  * default.
  */
private[modules] def githubCopilot: Try[Module] =
  for {
    encodedPostCreateScript <- Command.fromResourceScript("githubCopilotPostCreate.sh")
  } yield Module(
    name = "github-copilot",
    summary = "Sets up GitHub Copilot for IDE and CLI use (requires the mise module)",
    enabledByDefault = true,
    contribution = ModuleContribution(
      postCreateCommands = List(encodedPostCreateScript),
      // IDE integration for GitHub Copilot
      plugins = Plugins(
        intellij = List("GitHub.copilot"),
        vscode = List("com.github.copilot")
      )
    )
  )
