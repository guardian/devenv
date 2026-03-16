package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Mount, Plugins}

import scala.util.Try

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] def scalaLang(mountKey: String): Try[Module] = {
  // These are the locations which needs chown'ing (directories are created up to the mount point, but owned by root.
  val coursierCacheLocationRoot = "/home/vscode/.cache"
  val ivy2CacheLocationRoot     = "/home/vscode/.ivy2"

  // But _these_ are the locations we actually need to populate!
  // If we just cache the root locations, then we run the risk of sharing lockfiles
  // across containers, with unpredictable results.
  val coursierCacheLocation = s"$coursierCacheLocationRoot/coursier/v1"
  val ivy2CacheLocation     = s"$ivy2CacheLocationRoot/cache"

  Command.fromResourceScript("scala.sh").map { scalaCommand =>
    Module(
      name = "scala",
      summary = "Add IDE plugins and jar caching for Scala development",
      enabledByDefault = false,
      contribution = ModuleContribution(
        plugins = Plugins(
          intellij = List("org.intellij.scala"),
          vscode = List("scala-lang.scala")
        ),
        postCreateCommands = List(scalaCommand),
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
}
