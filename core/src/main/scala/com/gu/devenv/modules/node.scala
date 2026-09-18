package com.gu.devenv.modules

import com.gu.devenv.Plugins
import com.gu.devenv.modules.Modules.{Module, ModuleAdoption, ModuleContribution}

/** Provides IDE plugin support for Node.js development.
  *
  * This module adds Node.js language support plugins for IntelliJ IDEA. VS Code has built-in
  * Node.js support, so no additional plugins are needed.
  */
private[modules] val nodeLang = Module(
  name = "node",
  summary = "Add IDE plugins for Node.js development",
  adoption = ModuleAdoption.OptIn,
  contribution = ModuleContribution(
    plugins = Plugins(
      intellij = List("NodeJS"),
      vscode = List() // VS Code has built-in Node.js support
    )
  )
)
