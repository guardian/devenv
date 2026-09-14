package com.gu.devenv

import io.circe.{Encoder, Json}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues

class CodecTest extends AnyFreeSpec with Matchers with TryValues with HavingMatchers {
  "container size decoder" - {
    val custom = Json.obj(
      "memory"  -> Json.fromString("15g"),
      "cpus"    -> Json.fromInt(4),
      "shmSize" -> Json.fromString("512m")
    )

    "accepts small" in {
      Json.fromString("small").as[ContainerSize] shouldBe Right(ContainerSize.Small)
    }

    "accepts large" in {
      Json.fromString("large").as[ContainerSize] shouldBe Right(ContainerSize.Large)
    }

    "accepts a custom size with integer CPUs" in {
      custom.as[ContainerSize] shouldBe Right(ContainerSize.Custom("15g", BigDecimal(4), "512m"))
    }

    "accepts fractional CPUs" in {
      val json = custom.mapObject(_.add("cpus", Json.fromBigDecimal(BigDecimal("2.5"))))
      json.as[ContainerSize] shouldBe Right(ContainerSize.Custom("15g", BigDecimal("2.5"), "512m"))
    }

    "rejects unknown presets" in {
      Json.fromString("medium").as[ContainerSize].isLeft shouldBe true
    }

    "requires all three custom fields" in {
      custom.mapObject(_.remove("shmSize")).as[ContainerSize].isLeft shouldBe true
    }

    "rejects string CPUs" in {
      custom.mapObject(_.add("cpus", Json.fromString("2.5"))).as[ContainerSize].isLeft shouldBe true
    }

    "rejects numeric memory" in {
      custom.mapObject(_.add("memory", Json.fromInt(15))).as[ContainerSize].isLeft shouldBe true
    }
  }

  "project configuration decoding" - {
    "parses a complete project config from YAML" in {
      val projectConfig = Config
        .parseProjectConfig(scala.io.Source.fromResource("projectConfig.yaml").mkString)
        .success
        .value

      projectConfig should have(
        "name" as "Scala SBT Development Container",
        "modules" as List("mise"),
        "forwardPorts" as List(
          ForwardPort.SamePort(8080),
          ForwardPort.DifferentPorts(8000, 9000)
        ),
        "remoteEnv" as List(
          Env("SBT_OPTS", "-Xmx2G -XX:+UseG1GC"),
          Env("JAVA_HOME", "/usr/lib/jvm/java-17-openjdk-amd64")
        ),
        "containerEnv" as List(Env("EXAMPLE", "foo"), Env("EXAMPLE+2", "bar")),
        "mounts" as List(
          Mount.ExplicitMount("${localWorkspaceFolder}/.ivy2", "/home/vscode/.ivy2", "volume"),
          Mount.ExplicitMount("${localWorkspaceFolder}/.sbt", "/home/vscode/.sbt", "volume")
        ),
        "postCreateCommand" as List(
          Command("sbt update", "/workspaces/project/subdir", Some("postCreateCommand1")),
          Command("sbt compile", "subdir", Some("postCreateCommand2"))
        ),
        "postStartCommand" as List(
          Command("echo 'Container started successfully'", ".", Some("postStartCommand"))
        ),
        "features" as Map("ghcr.io/devcontainers/features/docker-in-docker:1" -> Json.obj()),
        "updateRemoteUserUID" as true
      )
    }

    "allows container size to be omitted" in {
      Config.parseProjectConfig("name: test").success.value.containerSize shouldBe None
    }

    "accepts a custom container size" in {
      val yaml = "name: test\ncontainerSize: { memory: 15g, cpus: 4, shmSize: 512m }"
      Config.parseProjectConfig(yaml).success.value.containerSize shouldBe
        Some(ContainerSize.Custom("15g", BigDecimal(4), "512m"))
    }

    "rejects an explicit null container size" in {
      Config.parseProjectConfig("name: test\ncontainerSize: null").isFailure shouldBe true
    }
  }

  "user configuration decoding" - {
    "parses a complete user config from YAML" in {
      val userConfig = Config
        .parseUserConfig(scala.io.Source.fromResource("userConfig.yaml").mkString)
        .success
        .value

      userConfig shouldBe UserConfig(
        plugins = Some(
          Plugins(
            List("com.github.copilot", "com.github.gtache.lsp"),
            List("GitHub.copilot")
          )
        ),
        dotfiles = Some(Dotfiles("https://github.com/example/dotfiles.git", "~", "install.sh")),
        containerSize = Some(ContainerSize.Small)
      )
    }

    "accepts a custom container size" in {
      val yaml = "containerSize: { memory: 15g, cpus: 4, shmSize: 512m }"
      Config.parseUserConfig(yaml).success.value.containerSize shouldBe
        Some(ContainerSize.Custom("15g", BigDecimal(4), "512m"))
    }

    "rejects an explicit null container size" in {
      Config.parseUserConfig("containerSize: null").isFailure shouldBe true
    }

    "parses an empty file" in {
      Config.parseUserConfig("").success.value shouldBe UserConfig.empty
    }

    "parses a file containing only comments" in {
      Config
        .parseUserConfig("# A comment\n# Another comment\n")
        .success
        .value shouldBe UserConfig.empty
    }
  }

  "forward port codec" - {
    "decoder" - {
      "should decode same port correctly" in {
        val json = Json.fromInt(8080)
        json.as[ForwardPort] match {
          case Left(value)  => fail(s"Expected Right got Left($value")
          case Right(value) =>
            value shouldEqual ForwardPort.SamePort(8080)
        }

      }

      "should decode different ports correctly" in {
        val json = Json.fromString("8000:9000")
        json.as[ForwardPort] match {
          case Left(value)  => fail(s"Expected Right got Left($value")
          case Right(value) =>
            value shouldEqual ForwardPort.DifferentPorts(8000, 9000)
        }
      }
    }

    "encoder" - {
      "should encode same port correctly" in {
        val port = ForwardPort.SamePort(8080)
        val json = Encoder[ForwardPort].apply(port)
        json shouldEqual Json.fromInt(8080)
      }

      "should encode different ports correctly" in {
        val port = ForwardPort.DifferentPorts(8000, 9000)
        val json = Encoder[ForwardPort].apply(port)
        json shouldEqual Json.fromString("8000:9000")
      }
    }
  }
}
