package com.gu.devenv

import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

class CommandTest extends AnyFreeSpec with Matchers with TryValues {

  "Command.fromResourceScript" - {

    "given a valid script" - {
      val result = Command.fromResourceScript("valid.sh")

      "should succeed" in {
        result.isSuccess shouldBe true
      }

      "should produce a command that decodes to the original script content" in {
        val cmd = result.success.value.cmd
        // extract the Base64 payload from: printf '%s' "<payload>" | base64 -d | bash -euo pipefail
        val encoded = cmd.split('"')(1)
        val decoded = new String(Base64.getUrlDecoder.decode(encoded), UTF_8)
        decoded should startWith("#!/usr/bin/env bash")
      }

      "should use the default working directory" in {
        result.success.value.workingDirectory shouldBe "."
      }

      "should use the supplied working directory" in {
        val cmd = Command.fromResourceScript("valid.sh", "/workspace").success.value
        cmd.workingDirectory shouldBe "/workspace"
      }

      "should pipe through bash -euo pipefail" in {
        result.success.value.cmd should include("bash -euo pipefail")
      }

      "should encode the content using standard Base64" in {
        val cmd     = result.success.value.cmd
        val encoded = cmd.split('"')(1)
        // Standard Base64 must only contain [A-Za-z0-9+/=]
        encoded should fullyMatch regex "[A-Za-z0-9+/=]+"
      }
    }

    "given a script with no shebang line" - {
      val result = Command.fromResourceScript("no-shebang.sh")

      "should fail" in {
        result.isFailure shouldBe true
      }

      "should report that the shebang is missing" in {
        result.failure.exception.getMessage should include("shebang")
      }

      "should wrap the error with the resource name" in {
        result.failure.exception.getMessage should include("no-shebang.sh")
      }
    }

    "given an empty script" - {
      val result = Command.fromResourceScript("empty.sh")

      "should fail" in {
        result.isFailure shouldBe true
      }

      "should report that the resource is empty" in {
        result.failure.exception.getMessage should include("empty")
      }

      "should wrap the error with the resource name" in {
        result.failure.exception.getMessage should include("empty.sh")
      }
    }

    "given a script name that does not end in .sh" - {
      val result = Command.fromResourceScript("mise.bash")

      "should fail" in {
        result.isFailure shouldBe true
      }

      "should report that the script name must end in .sh" in {
        result.failure.exception.getCause.getMessage should include(".sh")
      }

      "should include the offending script name in the error" in {
        result.failure.exception.getMessage should include("mise.bash")
      }
    }

    "given a non-existent resource" - {
      val result = Command.fromResourceScript("does-not-exist.sh")

      "should fail" in {
        result.isFailure shouldBe true
      }

      "should wrap the error with the resource name" in {
        result.failure.exception.getMessage should include("does-not-exist.sh")
      }
    }
  }

  "renderCommand" - {
    "should render the command" in {
      val commandRendered = Command.renderCommand(Command("ls 1", "."))
      val pattern         = "cd . && ls 1".r
      pattern.matches(commandRendered) shouldBe (true)
    }
  }

  "renderCommandWithLogging" - {
    "should render the command in brackets with logging" in {
      val commandRendered = Command.renderCommandWithLogging(Command("ls 1", ".", Some("one")))
      val pattern =
        "\\(echo .* Starting one.* && \\(cd . && ls 1 && echo .* Finished one.*\\) \\|\\| echo .* Errored! one.*\\)".r
      pattern.matches(commandRendered) shouldBe (true)
    }
  }
}
