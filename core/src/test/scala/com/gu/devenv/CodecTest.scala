package com.gu.devenv

import io.circe.{Encoder, Json}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues

class CodecTest extends AnyFreeSpec with Matchers with TryValues with HavingMatchers {
  "container size decoder" - {
    "accepts small" in {
      Json.fromString("small").as[ContainerSize] shouldBe Right(ContainerSize.Small)
    }

    "accepts large" in {
      Json.fromString("large").as[ContainerSize] shouldBe Right(ContainerSize.Large)
    }

    "rejects unknown presets" in {
      Json.fromString("medium").as[ContainerSize].isLeft shouldBe true
    }

    "accepts a custom size with integer CPUs" in {
      Json
        .obj(
          "memory"  -> Json.fromString("15g"),
          "cpus"    -> Json.fromInt(4),
          "shmSize" -> Json.fromString("512m")
        )
        .as[ContainerSize] shouldBe Right(ContainerSize.Custom("15g", BigDecimal(4), "512m"))
    }

    "accepts fractional CPUs" in {
      val json = Json.obj(
        "memory"  -> Json.fromString("15g"),
        "cpus"    -> Json.fromBigDecimal(BigDecimal("2.5")),
        "shmSize" -> Json.fromString("512m")
      )
      json.as[ContainerSize] shouldBe Right(ContainerSize.Custom("15g", BigDecimal("2.5"), "512m"))
    }

    "requires all three custom fields" in {
      Json
        .obj(
          "memory" -> Json.fromString("15g"),
          "cpus"   -> Json.fromInt(4)
        )
        .as[ContainerSize]
        .isLeft shouldBe true
    }

    "rejects string CPUs" in {
      Json
        .obj(
          "memory"  -> Json.fromString("15g"),
          "cpus"    -> Json.fromString("2.5"),
          "shmSize" -> Json.fromString("512m")
        )
        .as[ContainerSize]
        .isLeft shouldBe true
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
