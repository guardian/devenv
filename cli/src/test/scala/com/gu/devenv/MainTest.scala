package com.gu.devenv

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MainTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  private val genKnownCmd = Gen.oneOf("init", "generate", "check", "update", "version")
  private val genHelpFlag = Gen.oneOf("help", "--help", "-h")

  "Main.parseCommand" - {

    "help" - {
      "standalone help flag resolves to Help" in
        forAll(genHelpFlag) { flag =>
          Main.parseCommand(Seq(flag)) shouldBe Main.Command.Help
        }

      "any command followed by a help flag resolves to Help" in
        forAll(genKnownCmd, genHelpFlag) { (cmd, flag) =>
          Main.parseCommand(Seq(cmd, flag)) shouldBe Main.Command.Help
        }
    }

    "commands" - {
      "init" in { Main.parseCommand(Seq("init")) shouldBe Main.Command.Init }
      "generate" in { Main.parseCommand(Seq("generate")) shouldBe Main.Command.Generate }
      "check" in { Main.parseCommand(Seq("check")) shouldBe Main.Command.Check }
      "update" in { Main.parseCommand(Seq("update")) shouldBe Main.Command.Update }
      "version" in { Main.parseCommand(Seq("version")) shouldBe Main.Command.Version }
      "--version" in { Main.parseCommand(Seq("--version")) shouldBe Main.Command.Version }
      "-v" in { Main.parseCommand(Seq("-v")) shouldBe Main.Command.Version }
    }

    "no args" in {
      Main.parseCommand(Seq.empty) shouldBe Main.Command.NoCommand
    }

    "unknown command" in {
      Main.parseCommand(Seq("foo")) shouldBe Main.Command.Unknown("foo")
    }
  }
}
