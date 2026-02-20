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
          cmd = """bash -c 'set -e && echo -e "\033[1;34m[setup] Setting up mise...\033[0m" && """ +
            // ensure correct ownership of the shared mise data volume
            "sudo chown -R vscode:vscode /mnt/mise-data && " +
            // install mise using bash-specific installer (adds activation to ~/.bashrc automatically)
            // then symlink to /usr/local/bin for system-wide availability across different IDEs
            """curl -fsSL https://mise.run/bash | sh && """ +
            """sudo ln -sf /home/vscode/.local/bin/mise /usr/local/bin/mise && """ +
            """mise --version && """ +
            // This enables the repository's config files
            // See: https://mise.jdx.dev/cli/trust.html
            """mise trust --yes || true && """ +
            // do a mise install and print a warning if it fails
            """(mise install || echo -e "\033[1;33m[setup] mise install failed. You may need to run mise install manually inside the container.\033[0m") && """ +
            // Make sure mise is active after installation
            "mise doctor && " +
            """echo -e "\033[1;32m[setup] mise setup complete.\033[0m"'""",
          workingDirectory = "."
        )
      ),
      // Adds mise shims to the PATH so that installed tools are available in the remote environment
      remoteEnv = List(
        Env("PATH", "${containerEnv:PATH}:/mnt/mise-data/shims")
      ),
      // Sets the MISE_DATA_DIR to a shared volume (defined below) to cache downloaded tools and versions
      containerEnv = List(
        Env("MISE_DATA_DIR", "/mnt/mise-data")
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
