package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

/** Creates a dedicated space for the ivy cache within the development container.
  *
  * /home/vscode/.ivy2 is a standard cache location for java dependencies.
  *
  */
private val ivy = getIvy()

/** Test-oriented method allowing for a specified volume name.
  * @param volumeName
  *   The name used by docker for the ivy cache volume.
  * @return
  */
private[modules] def getIvy(volumeName: String = "docker-ivy-data-volume") =
  Module(
    name = "ivy",
    summary = "Create a volume for the ivy cache",
    enabledByDefault = false,
    contribution = ModuleContribution(
      /* These commands set up ivy inside the container after creation.
       * Also logs some useful info (including the output from `ivy doctor`) to the terminal to help with debugging.
       */
      postCreateCommands = List(
        Command(
          cmd = """bash -c 'set -e && """ +
            // ensure correct ownership of the shared ivy data volume
            "sudo chown -R vscode:vscode /home/vscode/.ivy2 " +
          """'""",
          workingDirectory = "."
        )
      ),
      /* This mount persists the ivy cache across container recreations.
       *
       * This brings security trade-offs as the volume is shared between all containers using this module.
       * However, without this shared volume, any JVM build process would need to re-download and re-install
       * all jars every time the container is recreated, which would be a significant usability issue.
       */
      mounts = List(
        Mount.ExplicitMount(
          source = volumeName,
          target = "/home/vscode/.ivy2",
          `type` = "volume"
        )
      ),
      // provide IDE support for ivy
      plugins = Plugins.empty
    )
  )
