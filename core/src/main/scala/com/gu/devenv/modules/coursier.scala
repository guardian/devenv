package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

/** Creates a dedicated space for the coursier cache within the development container.
  *
  *  /.jbdevcontainer/coursier is a standard cache location for java dependencies.
  *
  */
private val coursier = getCoursier()

/** Test-oriented method allowing for a specified volume name.
  * @param volumeName
  *   The name used by docker for the coursier cache volume.
  * @return
  */
private[modules] def getCoursier(volumeName: String = "docker-coursier-data-volume") =
  Module(
    name = "coursier",
    summary = "Create a volume for the coursier cache",
    enabledByDefault = false,
    contribution = ModuleContribution(
      /* These commands set up coursier inside the container after creation.
       * Also logs some useful info (including the output from `coursier doctor`) to the terminal to help with debugging.
       */
      postCreateCommands = List(
        Command(
          cmd = """bash -c 'set -e && """ +
            // ensure correct ownership of the shared coursier data volume
            "sudo chown -R vscode:vscode /.jbdevcontainer/coursier " +
          """'""",
          workingDirectory = "."
        )
      ),
      /* This mount persists the coursier cache across container recreations.
       *
       * This brings security trade-offs as the volume is shared between all containers using this module.
       * However, without this shared volume, any JVM build process would need to re-download and re-install
       * all jars every time the container is recreated, which would be a significant usability issue.
       */
      mounts = List(
        Mount.ExplicitMount(
          source = volumeName,
          target = "/.jbdevcontainer/coursier",
          `type` = "volume"
        )
      ),
      // provide IDE support for coursier
      plugins = Plugins.empty
    )
  )
