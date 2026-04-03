package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Plugins}

import scala.util.Try

/** Enables the mise tool within the development container.
  *
  * Mise is a tool for managing development environments, allowing developers to easily install and
  * switch between different versions of tools and dependencies required for their projects.
  *
  * See: https://mise.run/
  *
  * Automatically setting `mise` up to work in a devcontainer is a bit of a challenge so we handle
  * the complexity in a module. This allows teams to have a consistent development environment
  * without needing to manually install and configure `mise` each time.
  */
private[modules] def mise: Try[Module] =
  for {
    encodedOnCreateScript   <- Command.fromResourceScript("miseOnCreateCommand.sh")
    encodedPostCreateScript <- Command.fromResourceScript("misePostCreateCommand.sh")
  } yield {
    Module(
      name = "mise",
      summary = "Install and configure mise for dev tools management (https://mise.jdx.dev/)",
      enabledByDefault = true,
      contribution = ModuleContribution(
        onCreateCommands = List(encodedOnCreateScript),
        postCreateCommands = List(encodedPostCreateScript),
        // provide IDE support for mise
        plugins = Plugins(
          intellij = List("com.github.l34130.mise"),
          vscode = List("hverlin.mise-vscode")
        )
      )
    )
  }
