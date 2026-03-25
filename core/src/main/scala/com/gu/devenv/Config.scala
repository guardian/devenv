package com.gu.devenv

import com.gu.devenv.ContainerSize.Small
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

  private[devenv] val fixedImage      = "mcr.microsoft.com/devcontainers/base:ubuntu"
  private[devenv] val fixedRemoteUser = "vscode"

  /** `image` and `RemoteUser` are fixed until we have a requirement to support other values.
    *
    * The `remoteUser` must match a user id provided by the image.
    */
  private[devenv] val fixedConfig = JsonObject(
    "image"      -> fixedImage.asJson,
    "remoteUser" -> fixedRemoteUser.asJson
  )

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

  private[devenv] val smallContainerRunArgs: List[String] = List("--memory=1g", "--cpus=1")
  private[devenv] val largeContainerRunArgs: List[String] =
    List("--memory=16g", "--cpus=8", "--shm-size=512m")
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

      // Large by default.  Devs have beefy laptops
      val runArgs = userConfig.containerSize match {
        case Some(Small) => smallContainerRunArgs
        case _           => largeContainerRunArgs
      }

      projectConfig.copy(
        plugins = mergedPlugins,
        onCreateCommand = projectConfig.onCreateCommand,
        postCreateCommand = dotfilesCommands ++ projectConfig.postCreateCommand,
        postStartCommand = projectConfig.postStartCommand,
        runArgs = runArgs ++ projectConfig.runArgs
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
          "onCreateCommand" -> combineCommands(
            config.onCreateCommand,
            s"/var/log/$onCreateLogName"
          ),
          "postCreateCommand" -> combineCommands(
            config.postCreateCommand,
            s"/var/log/$postCreateLogName"
          ),
          "postStartCommand" -> combineCommands(
            config.postStartCommand,
            s"/var/log/$postStartLogName"
          )
        ).collect { case (key, Some(value)) =>
          key -> Json.fromString(value)
        }
      )

      val baseConfig = fixedConfig deepMerge JsonObject(
        "name"           -> config.name.asJson,
        "customizations" -> customizations.asJson,
        "forwardPorts"   -> config.forwardPorts.asJson
      )

      // Add optional fields if they exist
      val withFeatures = if (config.features.nonEmpty) {
        baseConfig.add("features", config.features.asJson)
      } else baseConfig

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

      val withRunArgs = if (config.runArgs.nonEmpty) {
        withSecurityOpt.add("runArgs", config.runArgs.asJson)
      } else withSecurityOpt

      commands.deepMerge(withRunArgs).asJson
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

  /** Compares the expected devcontainer.json content with the actual discovered content.
    *
    * If user configuration is present, the user devcontainer file must match the expected content.
    * Otherwise, we allow a missing (or empty) user devcontainer file.
    */
  def compareDevcontainerFiles(
      expectedUserJson: String,
      actualUserJson: Option[String], // None if the file is missing
      expectedSharedJson: String,
      actualSharedJson: String,
      devcontainerDir: Path,
      userConfigExists: Boolean
  ): CheckResult = {
    val userDevcontainerPath   = s"${devcontainerDir.getFileName}/user/devcontainer.json"
    val sharedDevcontainerPath = s"${devcontainerDir.getFileName}/shared/devcontainer.json"

    /** we allow the user devcontainer file to be missing if there is no user configuration and the
      * actual user devcontainer file is empty or missing
      */
    val userConfigNeedsChecking = userConfigExists || actualUserJson.exists(_.trim.nonEmpty)

    val userMismatch = Option.when(
      userConfigNeedsChecking && !actualUserJson.contains(expectedUserJson)
    ) {
      FileDiff(userDevcontainerPath, expected = expectedUserJson, actual = actualUserJson)
    }
    val sharedMismatch = Option.when(expectedSharedJson != actualSharedJson) {
      FileDiff(
        sharedDevcontainerPath,
        expected = expectedSharedJson,
        actual = Some(actualSharedJson)
      )
    }

    (userMismatch, sharedMismatch) match {
      case (None, None) =>
        CheckResult.Match(
          userPath = Option.when(actualUserJson.isDefined)(userDevcontainerPath),
          sharedPath = sharedDevcontainerPath
        )
      case _ =>
        CheckResult.Mismatch(
          userMismatch,
          sharedMismatch,
          userDevcontainerPath,
          sharedDevcontainerPath
        )
    }
  }

  private[devenv] def combineCommands(commands: List[Command], logFile: String): Option[String] =
    if (commands.isEmpty) None
    else {
      val renderedCommands = commands.map(Command.renderCommandWithLogging).mkString(" && ")
      Some(s"($renderedCommands) | sudo tee $logFile")
    }

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
      ".",
      Some("clone")
    )
    val installCommand = Command(
      dotfiles.installCommand,
      dotfiles.targetPath,
      Some("dotfiles")
    )
    List(cloneCommand, installCommand)
  }

  private[devenv] val postStartLogName  = "post-start.log"
  private[devenv] val onCreateLogName   = "on-create.log"
  private[devenv] val postCreateLogName = "post-create.log"

}
