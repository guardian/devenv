package com.gu.devenv.modules

import cats.implicits.*
import com.gu.devenv.*
import io.circe.Json

import scala.util.{Failure, Success, Try}

object Modules {
  // all registered modules are here
  // In the future we might provide ways to register custom modules but this is fine for now
  def builtInModules(moduleConfig: ModuleConfig): List[Module] =
    List(
      mise(moduleConfig.mountKey),
      dockerInDocker,
      scalaLang,
      nodeLang,
      ivy(moduleConfig.mountKey),
      coursier(moduleConfig.mountKey)
    )

  case class Module(
      name: String,
      summary: String,
      enabledByDefault: Boolean,
      contribution: ModuleContribution
  )
  case class ModuleContribution(
      features: Map[String, Json] = Map.empty,
      mounts: List[Mount] = Nil,
      plugins: Plugins = Plugins.empty,
      containerEnv: List[Env] = Nil,
      remoteEnv: List[Env] = Nil,
      postCreateCommands: List[Command] = Nil,
      capAdd: List[String] = Nil,
      securityOpt: List[String] = Nil
  )

  case class ModuleConfig(
      mountKey: String // allows test modules to use unique mount names
  )

  /** Apply modules to a project config, merging their contributions. Explicit config takes
    * precedence over module defaults. Returns a Failure if any unknown modules are specified.
    */
  def applyModules(config: ProjectConfig, modules: List[Module]): Try[ProjectConfig] =
    config.modules
      .traverse(getModuleContribution(modules)) // lookup requested modules to check we support them
      .map { moduleContributions =>
        moduleContributions.foldRight(config)((contribution, cfg) =>
          applyModuleContribution(cfg, contribution)
        )
      }

  /** Apply a single module contribution to a project config. Module contributions are prepended to
    * explicit config, so explicit config takes precedence.
    */
  private[devenv] def applyModuleContribution(
      config: ProjectConfig,
      contribution: ModuleContribution
  ): ProjectConfig =
    config.copy(
      features = contribution.features ++ config.features,
      mounts = contribution.mounts ++ config.mounts,
      plugins = Plugins(
        intellij = contribution.plugins.intellij ++ config.plugins.intellij,
        vscode = contribution.plugins.vscode ++ config.plugins.vscode
      ),
      containerEnv = contribution.containerEnv ++ config.containerEnv,
      remoteEnv = contribution.remoteEnv ++ config.remoteEnv,
      postCreateCommand = contribution.postCreateCommands ++ config.postCreateCommand,
      capAdd = contribution.capAdd ++ config.capAdd,
      securityOpt = contribution.securityOpt ++ config.securityOpt
    )

  private def getModuleContribution(modules: List[Module])(
      moduleName: String
  ): Try[ModuleContribution] =
    modules.find(_.name == moduleName) match {
      case Some(module) => Success(module.contribution)
      case None         => Failure(new IllegalArgumentException(s"Unknown module: '$moduleName'"))
    }
}
