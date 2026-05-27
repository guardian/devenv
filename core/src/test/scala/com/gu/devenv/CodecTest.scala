package com.gu.devenv

import io.circe.{Encoder, Json}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CodecTest extends AnyFreeSpec with Matchers {
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
