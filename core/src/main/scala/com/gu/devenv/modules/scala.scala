package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] def scalaLang(mountKey: String) = Module(
  name = "scala",
  summary = "Add IDE plugins for Scala development",
  enabledByDefault = false,
  contribution = ModuleContribution(
    plugins = Plugins(
      intellij = List("org.intellij.scala"),
      vscode = List("scala-lang.scala")
    ),
    postCreateCommands = List(
      Command(
        cmd = """bash -c '
            |set -e &&
            |echo -e "\033[1;34m[setup] Setting up .ivy2 ...\033[0m" &&
            |sudo chown -R vscode:vscode /home/vscode/.ivy2 &&
            |
            |echo -e "\033[1;34m[setup] Setting up coursier ...\033[0m" &&
            |sudo chown -R vscode:vscode /.jbdevcontainer/coursier
            |'
            |""".stripMargin.split('\n').mkString(" "),
        workingDirectory = "."
      )
    ),
    /* All mounts bring security trade-offs as the volume is shared between all containers using this module.  */
    mounts = List(
      /* This mount persists the coursier cache across container recreations. */
      Mount.ExplicitMount(
        source = s"$mountKey-coursier-data-volume",
        target = "/.jbdevcontainer/coursier",
        `type` = "volume"
      ),
      /* This mount persists the ivy2 cache across container recreations. */
      Mount.ExplicitMount(
        source = s"$mountKey-ivy-data-volume",
        target = "/home/vscode/.ivy2",
        `type` = "volume"
      )
    )
  )
)
