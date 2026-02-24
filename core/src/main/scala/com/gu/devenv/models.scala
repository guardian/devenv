package com.gu.devenv

import cats.*
import cats.syntax.all.*
import com.gu.devenv.Filesystem.{FileSystemStatus, GitignoreStatus}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

import java.nio.file.Path
import scala.util.Try

case class ProjectConfig(
    name: String,
    image: String = "mcr.microsoft.com/devcontainers/base:ubuntu",
    modules: List[String] = Nil,
    forwardPorts: List[ForwardPort] = Nil,
    remoteEnv: List[Env] = Nil,
    containerEnv: List[Env] = Nil,
    plugins: Plugins = Plugins.empty,
    mounts: List[Mount] = Nil,
    postCreateCommand: List[Command] = Nil,
    postStartCommand: List[Command] = Nil,
    features: Map[String, Json] = Map.empty,
    containerSize: Option[ContainerSize] = None,
    remoteUser: String = "vscode",
    updateRemoteUserUID: Boolean = true,
    capAdd: List[String] = Nil,
    securityOpt: List[String] = Nil
)

case class UserConfig(
    plugins: Option[Plugins],
    dotfiles: Option[Dotfiles]
)
object UserConfig {
  val empty = UserConfig(None, None)
}

enum ForwardPort {
  case SamePort(port: Int)
  case DifferentPorts(hostPort: Int, containerPort: Int)
}

object ContainerSize {
  given Decoder[ContainerSize] = Decoder.decodeString.emap {
    case "small" => Right(ContainerSize.Small)
    case "large" => Right(ContainerSize.Large)
    case s       => Left(s"Unknown container size: $s")
  }
}
enum ContainerSize {
  case Small, Large
}

object ForwardPort {

  /** we express container/host port forwards as either:
    *   - an Int (same port) (e.g. 8080)
    *   - a String with format "hostPort:containerPort" (e.g. "8000:9000")
    */
  given Decoder[ForwardPort.SamePort] = Decoder[Int].flatMap { portNumber =>
    Decoder.instance { c =>
      validatePortNumber(portNumber, c).map { validPort =>
        SamePort(validPort)
      }
    }
  }

  given Decoder[ForwardPort.DifferentPorts] = Decoder[String].flatMap { portString =>
    Decoder.instance { c =>
      portString.split(":") match {
        case Array(hostPortStr, containerPortStr) =>
          for {
            hostPort <- Try(hostPortStr.toInt).toEither.leftMap(_ =>
              DecodingFailure(
                s"Invalid host port: $hostPortStr",
                c.history
              )
            )
            containerPort <- Try(containerPortStr.toInt).toEither.leftMap(_ =>
              DecodingFailure(
                s"Invalid container port: $containerPortStr",
                c.history
              )
            )
            validHostPort      <- validatePortNumber(hostPort, c)
            validContainerPort <- validatePortNumber(containerPort, c)
          } yield DifferentPorts(validHostPort, validContainerPort)
        case _ =>
          Left(
            DecodingFailure(
              s"Invalid port mapping format: $portString. Expected format 'hostPort:containerPort'.",
              c.history
            )
          )
      }
    }
  }
  given Decoder[ForwardPort] = summon[Decoder[ForwardPort.SamePort]]
    .or(summon[Decoder[ForwardPort.DifferentPorts]].widen)

  private def validatePortNumber(
      port: Int,
      c: io.circe.HCursor
  ): Either[DecodingFailure, Int] =
    if (port >= 1 && port <= 65535)
      Right(port)
    else
      Left(
        DecodingFailure(
          s"Invalid port number: $port. Port numbers must be between 1 and 65535.",
          c.history
        )
      )

  given Encoder[ForwardPort] = Encoder.instance {
    case SamePort(port) => Json.fromInt(port)
    case DifferentPorts(hostPort, containerPort) =>
      Json.fromString(s"$hostPort:$containerPort")
  }
}
case class Env(name: String, value: String)
case class Plugins(
    intellij: List[String],
    vscode: List[String]
)
object Plugins {
  def empty = Plugins(Nil, Nil)
}
case class Command(
    cmd: String,
    workingDirectory: String
)
enum Mount {
  case ExplicitMount(
      source: String,
      target: String,
      `type`: String
  )
  case ShortMount(
      mount: String
  )
}

object Mount {
  import io.circe.{Decoder, Encoder}

  given Decoder[Mount] = Decoder.instance { c =>
    c.as[String].map(ShortMount(_)) match {
      case Right(shortMount) => Right(shortMount)
      case Left(_) =>
        for {
          source    <- c.downField("source").as[String]
          target    <- c.downField("target").as[String]
          mountType <- c.downField("type").as[String]
        } yield ExplicitMount(source, target, mountType)
    }
  }

  implicit val encodeMountEncoder: Encoder[Mount] = Encoder.instance {
    case ShortMount(mount) => Json.fromString(mount)
    case ExplicitMount(source, target, mountType) =>
      Json.obj(
        "source" -> Json.fromString(source),
        "target" -> Json.fromString(target),
        "type"   -> Json.fromString(mountType)
      )
  }
}

/** Allows automatic provisioning of a user's dotfiles via a git repository.
  *
  * See also:
  * https://code.visualstudio.com/docs/devcontainers/containers#_personalizing-with-dotfile-repositories
  */
case class Dotfiles(
    repository: String,
    targetPath: String,
    installCommand: String
)

// Program execution types

case class DevEnvPaths(
    devcontainerDir: Path,
    userDir: Path,
    userDevcontainerFile: Path,
    sharedDir: Path,
    sharedDevcontainerFile: Path,
    gitignoreFile: Path,
    devenvFile: Path
)

case class UserConfigPaths(
    devenvConf: Path
)

case class FileDiff(
    path: String,
    expected: String,
    actual: String
)

// results

case class InitResult(
    devcontainerStatus: FileSystemStatus,
    userStatus: FileSystemStatus,
    sharedStatus: FileSystemStatus,
    gitignoreStatus: GitignoreStatus,
    devenvStatus: FileSystemStatus
)

enum GenerateResult(val successful: Boolean) {
  case Success(
      userDevcontainerStatus: FileSystemStatus,
      sharedDevcontainerStatus: FileSystemStatus
  ) extends GenerateResult(successful = true)

  case NotInitialized extends GenerateResult(successful = false)

  case ConfigNotCustomized extends GenerateResult(successful = false)
}

enum CheckResult(val successful: Boolean) {
  case Match(
      userPath: String,
      sharedPath: String
  ) extends CheckResult(successful = true)

  case Mismatch(
      userMismatch: Option[FileDiff],
      sharedMismatch: Option[FileDiff],
      userPath: String,
      sharedPath: String
  ) extends CheckResult(successful = false)

  case NotInitialized extends CheckResult(successful = false)
}
