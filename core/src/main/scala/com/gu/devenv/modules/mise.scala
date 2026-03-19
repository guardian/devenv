package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Env, Mount, Plugins}

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
private[modules] def mise(mountKey: String): Try[Module] =
  for {
    encodedOnCreateScript   <- Command.fromResourceScript("miseOnCreateCommand.sh")
    encodedPostCreateScript <- Command.fromResourceScript("misePostCreateCommand.sh")
  } yield {
    // Sets the DEVENV_MISE_CACHE_MOUNT_DIR to a shared volume (defined below) to cache downloaded tools and versions
    val DEVENV_MISE_CACHE_MOUNT_DIR = "/mnt/mise-cache"
    // This is not a DEVENV var, it's a MISE var, so no DEVENV_ namespacing.
    val MISE_INSTALL_PATH = s"$DEVENV_MISE_CACHE_MOUNT_DIR/mise"
    Module(
      name = "mise",
      summary = "Install and configure mise for dev tools management (https://mise.jdx.dev/)",
      enabledByDefault = true,
      contribution = ModuleContribution(
        onCreateCommands = List(encodedOnCreateScript),
        postCreateCommands = List(encodedPostCreateScript),
        // Adds mise shims to the PATH so that installed tools are available in the remote environment
        remoteEnv = List(
          Env("PATH", s"$${containerEnv:PATH}:~/.local/share/mise/shims/")
        ),
        containerEnv = List(
          Env("DEVENV_MISE_CACHE_MOUNT_DIR", DEVENV_MISE_CACHE_MOUNT_DIR),
          Env("MISE_INSTALL_PATH", MISE_INSTALL_PATH)
        ),
        /* This mount persists the mise cache and installed tool versions across container recreations.
         *
         * This brings security trade-offs as the volume is shared between all containers using this module.
         * However, without this shared volume, mise would need to re-download and re-install tools
         * every time the container is recreated, which would be a significant usability issue.
         */
        mounts = List(
          Mount.ExplicitMount(
            source = s"$mountKey-mise-cache-volume",
            target = DEVENV_MISE_CACHE_MOUNT_DIR,
            `type` = "volume"
          )
        ),
        // provide IDE support for mise
        plugins = Plugins(
          intellij = List("com.github.l34130.mise"),
          vscode = List("hverlin.mise-vscode")
        )
      )
    )
  }
