package com.gu.devenv.modules

//import com.gu.devenv.*
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import cats.implicits.*
import com.gu.devenv.*
import io.circe.Json

object Modules {
  // all registered modules are here
  // In the future we might provide ways to register custom modules but this is fine for now
  def builtInModules(moduleConfig: ModuleConfig): Try[List[Module]] = for {
    miseModule  <- mise(moduleConfig.mountKey)
    scalaModule <- scalaLang(moduleConfig.mountKey)
  } yield List(
    miseModule,
    dockerInDocker,
    scalaModule,
    nodeLang
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

  /** Loads a script from resource directory core/src/main/resources/com/gu/devenv/modules, encodes
    * it in base64 and returns the encoded string wrapped in a Try.
    *
    * This allows us to include a shell script in our project resources and execute it in the
    * container without needing to worry about escaping special characters or formatting issues that
    * can arise when embedding a script directly in the postCreateCommand.
    */
  def base64Encoded(scriptName: String): Try[String] = {
    val resource = s"com/gu/devenv/modules/$scriptName"
    Using(Source.fromResource(resource)) { source =>
      Base64.getEncoder.encodeToString(source.mkString.getBytes(UTF_8))
    }.recoverWith { case err =>
      Failure(new RuntimeException(s"Could not load resource $resource", err))
    }
  }

}
