package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] def scalaLang(mountKey: String) = {
  val coursierCacheLocation = "/home/vscode/.cache/coursier/v1"
  val ivy2CacheLocation     = "/home/vscode/.ivy2"
  Module(
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
          cmd = s"""bash -c '
            |set -e &&
            |echo -e "\\033[1;34m[setup] Setting up .ivy2 ...\\033[0m" &&
            |sudo chown -R vscode:vscode $ivy2CacheLocation &&
            |
            |echo -e "\\033[1;34m[setup] Setting up coursier ...\\033[0m" &&
            |sudo chown -R vscode:vscode $coursierCacheLocation
            |
            |'""".stripMargin.split('\n').mkString(" "),
          workingDirectory = "."
        )
      ),
      /* All mounts bring security trade-offs as the volume is shared between all containers using this module.  */
      mounts = List(
        /* This mount persists the coursier cache across container recreations. */
        Mount.ExplicitMount(
          source = s"$mountKey-coursier-data-volume",
          target = coursierCacheLocation,
          `type` = "volume"
        ),
        /* This mount persists the ivy2 cache across container recreations. */
        Mount.ExplicitMount(
          source = s"$mountKey-ivy-data-volume",
          target = ivy2CacheLocation,
          `type` = "volume"
        )
      )
    )
  )
}
