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

    val DEVENV_IVY_CACHE_MOUNT_DIR      = "/mnt/ivy-data"
    val DEVENV_COURSIER_CACHE_MOUNT_DIR = "/mnt/coursier-data"

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
          Env("DEVENV_IVY_CACHE_MOUNT_DIR", DEVENV_IVY_CACHE_MOUNT_DIR),
          Env("DEVENV_COURSIER_CACHE_MOUNT_DIR", DEVENV_COURSIER_CACHE_MOUNT_DIR)
        ),
        /* All mounts bring security trade-offs as the volume is shared between all containers using this module.  */
        mounts = List(
          /* This mount persists the coursier cache across container recreations. */
          Mount.ExplicitMount(
            source = s"$mountKey-coursier-data-volume",
            target = DEVENV_COURSIER_CACHE_MOUNT_DIR,
            `type` = "volume"
          ),
          /* This mount persists the ivy2 cache across container recreations. */
          Mount.ExplicitMount(
            source = s"$mountKey-ivy-data-volume",
            target = DEVENV_IVY_CACHE_MOUNT_DIR,
            `type` = "volume"
          )
        )
      )
    )
  }
