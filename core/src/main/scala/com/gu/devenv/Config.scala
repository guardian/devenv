package com.gu.devenv

import com.gu.devenv.modules.Modules
import com.gu.devenv.modules.Modules.Module
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto.*
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser
import io.circe.{Json, JsonObject}

import java.nio.file.Path
import scala.util.Try

object Config {
  given Configuration = Configuration.default.withDefaults

  def loadProjectConfig(path: Path): Try[ProjectConfig] =
    for {
      configStr <- Filesystem.readFile(path)
      config    <- parseProjectConfig(configStr)
    } yield config

  def loadUserConfig(path: Path): Try[Option[UserConfig]] =
    Filesystem
      .readFile(path)
      .flatMap(parseUserConfig)
      .map(Some(_))
      .recover { case _: java.nio.file.NoSuchFileException =>
        // It's ok if the user config file doesn't exist
        None
      }

  def parseProjectConfig(contents: String): Try[ProjectConfig] =
    for {
      json          <- parser.parse(contents).toTry
      projectConfig <- json.as[ProjectConfig].toTry
    } yield projectConfig

  def parseUserConfig(contents: String): Try[UserConfig] =
    if (yamlIsEmpty(contents)) {
      scala.util.Success(UserConfig(None, None))
    } else {
      for {
        json       <- parser.parse(contents).toTry
        userConfig <- json.as[UserConfig].toTry
      } yield userConfig
    }

  def mergeConfigs(
      projectConfig: ProjectConfig,
      maybeUserConfig: Option[UserConfig]
  ): ProjectConfig =
    maybeUserConfig.fold(projectConfig) { userConfig =>
      // fetch the user's configured items so they can be added to the project config
      val mergedPlugins =
        applyPlugins(projectConfig.plugins, userConfig.plugins)
      val dotfilesCommands = userConfig.dotfiles
        .map(applyDotfiles)
        .getOrElse(Nil)

      projectConfig.copy(
        plugins = mergedPlugins,
        postCreateCommand = dotfilesCommands ++ projectConfig.postCreateCommand,
        postStartCommand = projectConfig.postStartCommand
      )
    }

  def configAsJson(projectConfig: ProjectConfig, modules: List[Module]): Try[Json] =
    // Start by applying requested modules, then put explicit configuration on top of that
    Modules.applyModules(projectConfig, modules).map { config =>
      val customizations = JsonObject(
        "vscode" -> Json.obj(
          "extensions" -> config.plugins.vscode.asJson
        ),
        "jetbrains" -> Json.obj(
          "plugins" -> config.plugins.intellij.asJson
        )
      )

      val commands = JsonObject.fromIterable(
        List(
          "postCreateCommand" -> combineCommands(config.postCreateCommand),
          "postStartCommand"  -> combineCommands(config.postStartCommand)
        ).collect { case (key, Some(value)) =>
          key -> Json.fromString(value)
        }
      )

      val baseConfig = JsonObject(
        "name"           -> config.name.asJson,
        "image"          -> config.image.asJson,
        "customizations" -> customizations.asJson,
        "forwardPorts"   -> config.forwardPorts.asJson
      )

      // Default to large container.
      // This must be overridden for test cases as the GitHub actions environment will not support a "large" container.
      val withContainerSize =
        baseConfig.add("runArgs", config.containerSize.getOrElse(ContainerSize.`large`).toRunArgs.asJson)

      // Add optional fields if they exist
      val withFeatures = if (config.features.nonEmpty) {
        withContainerSize.add("features", config.features.asJson)
      } else withContainerSize

      val withMounts = if (config.mounts.nonEmpty) {
        withFeatures.add("mounts", config.mounts.asJson)
      } else withFeatures

      val withContainerEnv = if (config.containerEnv.nonEmpty) {
        withMounts.add("containerEnv", envListToJson(config.containerEnv))
      } else withMounts

      val withRemoteEnv = if (config.remoteEnv.nonEmpty) {
        withContainerEnv.add("remoteEnv", envListToJson(config.remoteEnv))
      } else withContainerEnv

      val withCapAdd = if (config.capAdd.nonEmpty) {
        withRemoteEnv.add("capAdd", config.capAdd.asJson)
      } else withRemoteEnv

      val withSecurityOpt = if (config.securityOpt.nonEmpty) {
        withCapAdd.add("securityOpt", config.securityOpt.asJson)
      } else withCapAdd

      commands.deepMerge(withSecurityOpt).asJson
    }

  def generateConfigs(
      projectConfig: ProjectConfig,
      maybeUserConfig: Option[UserConfig],
      modules: List[Module]
  ): Try[(String, String)] = {
    val mergedUserConfig = Config.mergeConfigs(projectConfig, maybeUserConfig)
    for {
      userJson   <- Config.configAsJson(mergedUserConfig, modules)
      sharedJson <- Config.configAsJson(projectConfig, modules)
    } yield (userJson.spaces2, sharedJson.spaces2)
  }

  def compareDevcontainerFiles(
      expectedUserJson: String,
      actualUserJson: String,
      expectedSharedJson: String,
      actualSharedJson: String,
      devcontainerDir: Path
  ): CheckResult = {
    val userDevcontainerPath   = s"${devcontainerDir.getFileName}/user/devcontainer.json"
    val sharedDevcontainerPath = s"${devcontainerDir.getFileName}/shared/devcontainer.json"

    val userMismatch = if (expectedUserJson != actualUserJson) {
      Some(FileDiff(userDevcontainerPath, expected = expectedUserJson, actual = actualUserJson))
    } else None

    val sharedMismatch = if (expectedSharedJson != actualSharedJson) {
      Some(
        FileDiff(sharedDevcontainerPath, expected = expectedSharedJson, actual = actualSharedJson)
      )
    } else None

    if (userMismatch.isEmpty && sharedMismatch.isEmpty) {
      CheckResult.Match(userDevcontainerPath, sharedDevcontainerPath)
    } else {
      CheckResult.Mismatch(
        userMismatch,
        sharedMismatch,
        userDevcontainerPath,
        sharedDevcontainerPath
      )
    }
  }

  private def combineCommands(commands: List[Command]): Option[String] =
    if (commands.isEmpty) None
    else
      Some(
        commands
          .map(command => s"(cd ${command.workingDirectory} && ${command.cmd})")
          .mkString(" && ")
      )

  private def envListToJson(envList: List[Env]): Json =
    Json.obj(
      envList.map(env => env.name -> Json.fromString(env.value)): _*
    )

  /** Parsing an empty YAML file throws an exception, but an empty YAML file is valid and should
    * result in an empty configuration. We check for empty contents here.
    */
  private def yamlIsEmpty(text: String): Boolean =
    text.linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .forall(_.startsWith("#"))

  private def applyPlugins(
      projectPlugins: Plugins,
      userPlugins: Option[Plugins]
  ): Plugins =
    Plugins(
      intellij = (projectPlugins.intellij ++ userPlugins.fold(Nil)(_.intellij)).distinct,
      vscode = (projectPlugins.vscode ++ userPlugins.fold(Nil)(_.vscode)).distinct
    )

  private def applyDotfiles(dotfiles: Dotfiles): List[Command] = {
    val cloneCommand = Command(
      s"git clone ${dotfiles.repository} ${dotfiles.targetPath}",
      "."
    )
    val installCommand = Command(
      dotfiles.installCommand,
      dotfiles.targetPath
    )
    List(cloneCommand, installCommand)
  }
}
