package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Env, Mount, Plugins}

import scala.util.Try

/** Provides IDE plugin support for Scala development.
  *
  * This module adds the Scala language plugin for both VS Code and IntelliJ IDEA.
  */
private[modules] def scalaLang(mountKey: String): Try[Module] =
  for {
    encodedOnCreateScript <- Command.fromResourceScript("scalaOnCreateCommand.sh")
  } yield {
    val COURSIER_DATA_DIR_ROOT = "/home/vscode/.cache"
    val IVY_DATA_DIR_ROOT      = "/home/vscode/.ivy2"
    val COURSIER_DATA_DIR      = s"$COURSIER_DATA_DIR_ROOT/coursier/v1"
    val IVY_DATA_DIR           = s"$IVY_DATA_DIR_ROOT/cache"

    Module(
      name = "scala",
      summary = "Add IDE plugins and jar caching for Scala development",
      enabledByDefault = false,
      contribution = ModuleContribution(
        plugins = Plugins(
          intellij = List("org.intellij.scala"),
          vscode = List("scala-lang.scala")
        ),
        onCreateCommands = List(encodedOnCreateScript),
        // Sets the roots of the cache volumes so that they can be appropriately chown'd
        containerEnv = List(
          Env("IVY_DATA_DIR_ROOT", IVY_DATA_DIR_ROOT),
          Env("COURSIER_DATA_DIR_ROOT", COURSIER_DATA_DIR_ROOT)
        ),
        /* All mounts bring security trade-offs as the volume is shared between all containers using this module.  */
        mounts = List(
          /* This mount persists the coursier cache across container recreations. */
          Mount.ExplicitMount(
            source = s"$mountKey-coursier-data-volume",
            target = COURSIER_DATA_DIR,
            `type` = "volume"
          ),
          /* This mount persists the ivy2 cache across container recreations. */
          Mount.ExplicitMount(
            source = s"$mountKey-ivy-data-volume",
            target = IVY_DATA_DIR,
            `type` = "volume"
          )
        )
      )
    )
  }
