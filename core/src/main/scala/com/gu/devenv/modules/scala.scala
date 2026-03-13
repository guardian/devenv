package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

import scala.util.Try

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] def scalaLang(mountKey: String): Try[Module] = for {
  encodedOnCreateScript <- Command.fromResourceScript("scalaOnCreateCommand.sh")
} yield Module(
  name = "scala",
  summary = "Add IDE plugins and jar caching for Scala development",
  enabledByDefault = false,
  contribution = ModuleContribution(
    plugins = Plugins(
      intellij = List("org.intellij.scala"),
      vscode = List("scala-lang.scala")
    ),
    postCreateCommands = List(encodedOnCreateScript),
    /* All mounts bring security trade-offs as the volume is shared between all containers using this module.  */
    mounts = List(
      /* This mount persists the coursier cache across container recreations. */
      Mount.ExplicitMount(
        source = s"$mountKey-coursier-data-volume",
        target = "/home/vscode/.cache/coursier/v1",
        `type` = "volume"
      ),
      /* This mount persists the ivy2 cache across container recreations. */
      Mount.ExplicitMount(
        source = s"$mountKey-ivy-data-volume",
        target = "/home/vscode/.ivy2/cache",
        `type` = "volume"
      )
    )
  )
)