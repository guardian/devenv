package com.gu.devenv.modules

import cats.implicits.*
import com.gu.devenv.*
import io.circe.Json

import scala.util.Try

object Modules {
  enum ModuleResolutionError {
    case UnknownModule(name: String)
    case UnknownDependency(module: String, dependency: String)
    case DependencyNotEnabled(module: String, dependency: String)
    case DependencyOutOfOrder(module: String, dependency: String)
  }

  private type ResolutionResult[A] = Either[ModuleResolutionError, A]

  // all registered modules are here
  // In the future we might provide ways to register custom modules but this is fine for now
  def builtInModules(moduleConfig: ModuleConfig): Try[List[Module]] =
    for {
      miseModule          <- mise
      scalaModule         <- scalaLang(moduleConfig.mountKey)
      githubCopilotModule <- githubCopilot
    } yield List(
      miseModule,
      dockerInDocker,
      scalaModule,
      nodeLang,
      githubCopilotModule
    )

  case class Module(
      name: String,
      summary: String,
      enabledByDefault: Boolean,
      contribution: ModuleContribution,
      dependsOn: List[String] = Nil
  )

  /** Project modules that have been looked up and whose dependencies have been validated. */
  final class ResolvedModules private[Modules] (val modules: List[Module])

  object ResolvedModules {
    val empty: ResolvedModules = new ResolvedModules(Nil)
  }

  case class ModuleContribution(
      features: Map[String, Json] = Map.empty,
      mounts: List[Mount] = Nil,
      plugins: Plugins = Plugins.empty,
      containerEnv: List[Env] = Nil,
      remoteEnv: List[Env] = Nil,
      onCreateCommands: List[Command] = Nil,
      postCreateCommands: List[Command] = Nil,
      capAdd: List[String] = Nil,
      securityOpt: List[String] = Nil
  )

  case class ModuleConfig(
      mountKey: String // allows test modules to use unique mount names
  )

  /** Resolve configured module names and validate their dependencies.
    *
    * The configured order is preserved because it determines both dependency ordering and the order
    * in which contributions are applied.
    */
  def resolveModules(
      moduleNames: List[String],
      availableModules: List[Module]
  ): Either[ModuleResolutionError, ResolvedModules] =
    moduleNames
      .traverse(getModule(availableModules))
      .flatMap { projectModules =>
        validateDependencies(projectModules, availableModules)
          .as(new ResolvedModules(projectModules))
      }

  /** Apply resolved modules to a project config, merging their contributions. Explicit config takes
    * precedence over module defaults.
    */
  private[devenv] def applyModules(
      config: ProjectConfig,
      resolvedModules: ResolvedModules
  ): ProjectConfig =
    resolvedModules.modules.foldRight(config)((module, cfg) =>
      applyModuleContribution(cfg, module.contribution)
    )

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
      onCreateCommand = contribution.onCreateCommands ++ config.onCreateCommand,
      postCreateCommand = contribution.postCreateCommands ++ config.postCreateCommand,
      capAdd = contribution.capAdd ++ config.capAdd,
      securityOpt = contribution.securityOpt ++ config.securityOpt
    )

  private def getModule(
      modules: List[Module]
  )(moduleName: String): Either[ModuleResolutionError, Module] =
    modules.find(_.name == moduleName) match {
      case Some(module) => Right(module)
      case None         => Left(ModuleResolutionError.UnknownModule(moduleName))
    }

  /** For each module, checks that its dependencies are present, and that those modules are earlier
    * in the list.
    *
    * Returns a typed error if any dependencies are missing, invalid, or out of order.
    */
  def validateDependencies(
      projectModules: List[Module],
      availableModules: List[Module]
  ): Either[ModuleResolutionError, Unit] = {
    val projectModuleNames   = projectModules.map(_.name)
    val availableModuleNames = availableModules.map(_.name)

    projectModules
      .foldM[ResolutionResult, Set[String]](Set.empty) { (accNames, module) =>
        module.dependsOn
          .traverse_[ResolutionResult, Unit] { dependency =>
            if (!availableModuleNames.contains(dependency))
              Left(
                ModuleResolutionError.UnknownDependency(module.name, dependency)
              )
            else if (!projectModuleNames.contains(dependency))
              Left(
                ModuleResolutionError.DependencyNotEnabled(module.name, dependency)
              )
            else if (!accNames.contains(dependency))
              Left(
                ModuleResolutionError.DependencyOutOfOrder(module.name, dependency)
              )
            else Right(())
          }
          .as(accNames + module.name)
      }
      .void
  }
}
