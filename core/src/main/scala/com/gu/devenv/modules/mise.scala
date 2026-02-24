package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Env, Mount, Plugins}

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
private[modules] def mise(mountKey: String) =
  Module(
    name = "mise",
    summary = "Install and configure mise for dev tools management (https://mise.jdx.dev/)",
    enabledByDefault = true,
    contribution = ModuleContribution(
      /* These commands set up mise inside the container after creation.
       * Also logs some useful info (including the output from `mise doctor`) to the terminal to help with debugging.
       */
      postCreateCommands = List(
        Command(
          cmd = """bash -c '
              |echo -e "\033[1;34m[setup] Setting up mise.\033[0m" &&
              |
              |echo -e "\033[1;34m[setup] ensure correct ownership of the shared mise data volume\033[0m" &&
              |sudo chown -R vscode:vscode /mnt/mise-data &&
              |
              |echo -e "\033[1;34m[setup] checking for mise already present\033[0m" &&
              |(test -f $MISE_INSTALL_PATH ]] && echo -e "\033[1;34m[setup] mise is present\033[0m") ||
              |(echo -e "\033[1;34m[setup] Installing mise...\033[0m" && curl -fsSL https://mise.run/bash | sh) &&
              |
              |echo -e "\033[1;34m[setup] Symlinking $MISE_INSTALL_PATH to /usr/local/bin/mise...\033[0m" &&
              |sudo ln -sf $MISE_INSTALL_PATH /usr/local/bin/mise &&
              |
              |echo -e "\033[1;34m[setup] checking mise working correctly\033[0m" &&
              |mise --version &&
              |
              |echo -e "\033[1;34m[setup] updating mise if needed\033[0m" &&
              |(mise self-update || echo "Skipping mise self-update - you may be offline") &&
              |
              |echo -e "\033[1;34m[setup] trusting (See: https://mise.jdx.dev/cli/trust.html)\033[0m" &&
              |(mise trust --yes || true) &&
              |
              |(mise install || echo -e "\033[1;33m[setup] mise install failed. You may need to run mise install manually inside the container.\033[0m") &&
              |
              |echo -e "\033[1;34m[setup] final checks\033[0m" &&
              |mise doctor &&
              |
              |echo -e "\033[1;32m[setup] mise setup complete at $MISE_INSTALL_PATH.\033[0m"
              |
              |'
              |""".stripMargin.split('\n').mkString(" "),
          workingDirectory = "."
        )
      ),
      // Adds mise shims to the PATH so that installed tools are available in the remote environment
      remoteEnv = List(
        Env("PATH", "${containerEnv:PATH}:/mnt/mise-data/shims")
      ),
      // Sets the MISE_DATA_DIR to a shared volume (defined below) to cache downloaded tools and versions
      containerEnv = List(
        Env("MISE_DATA_DIR", "/mnt/mise-data"),
        Env("MISE_INSTALL_PATH", "/mnt/mise-data/mise")
      ),
      /* This mount persists the mise cache and installed tool versions across container recreations.
       *
       * This brings security trade-offs as the volume is shared between all containers using this module.
       * However, without this shared volume, mise would need to re-download and re-install tools
       * every time the container is recreated, which would be a significant usability issue.
       */
      mounts = List(
        Mount.ExplicitMount(
          source = s"$mountKey-mise-data-volume",
          target = "/mnt/mise-data",
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
