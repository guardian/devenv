package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] val scalaLang = getScalaLang();
private[modules] def getScalaLang(volumeName: String = "docker-sbt-data-volume") = Module(
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
        cmd = """bash -c 'set -e && """ +
          // ensure correct ownership of the shared ivy data volume
          "sudo chown -R vscode:vscode /home/vscode/.sbt " +
          """'""",
        workingDirectory = "."
      )
    ),
    mounts = List(
      Mount.ExplicitMount(
        source = volumeName,
        target = "/home/vscode/.sbt",
        `type` = "volume"
      )
    )
  )
)
