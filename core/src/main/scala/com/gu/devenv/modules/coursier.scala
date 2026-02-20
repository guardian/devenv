package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

/** Creates a dedicated space for the coursier cache within the development container.
  *
  *  /.jbdevcontainer/coursier is a standard cache location for jar dependencies of jvm projects.
  *
  */
private[modules] def coursier(mountKey: String) =
  Module(
    name = "coursier",
    summary = "Create a volume for the coursier cache",
    enabledByDefault = false,
    contribution = ModuleContribution(
      /* These commands set up the coursier cache inside the container after creation.
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
          source = s"$mountKey-coursier-data-volume",
          target = "/.jbdevcontainer/coursier",
          `type` = "volume"
        )
      ),
      // provide IDE support for coursier
      plugins = Plugins.empty
    )
  )
