package com.gu.devenv.modules

import cats.implicits.*
import com.gu.devenv.*
import io.circe.Json

import scala.util.{Failure, Success, Try}

object Modules {
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
      onCreateCommand = contribution.onCreateCommands ++ config.onCreateCommand,
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

  /** For each module, checks that its dependencies are present, and that those modules are earlier
    * in the list.
    *
    * Returns a Failure if any dependencies are missing, invalid, or out of order.
    */
  def validateDependencies(
      projectModules: List[Module],
      availableModules: List[Module]
  ): Try[Unit] = {
    val projectModuleNames   = projectModules.map(_.name)
    val availableModuleNames = availableModules.map(_.name)

    projectModules
      .foldM[Try, Set[String]](Set.empty) { (accNames, module) =>
        module.dependsOn
          .traverse_[Try, Unit] { dependency =>
            if (!availableModuleNames.contains(dependency))
              Failure(
                new IllegalArgumentException(
                  s"Module '${module.name}' depends on unknown module '$dependency'"
                )
              )
            else if (!projectModuleNames.contains(dependency))
              Failure(
                new IllegalArgumentException(
                  s"Module '${module.name}' depends on '$dependency', but it is not enabled in the project"
                )
              )
            else if (!accNames.contains(dependency))
              Failure(
                new IllegalArgumentException(
                  s"Module '${module.name}' depends on '$dependency', so it must appear before '${module.name}' in the project modules list"
                )
              )
            else Success(())
          }
          .as(accNames + module.name)
      }
      .void
  }
}
