package com.gu.devenv

import com.gu.devenv.Filesystem.{FileSystemStatus, GitignoreStatus}
import fansi.Str
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class OutputFormatterTest extends AnyFreeSpec with Matchers {

  "OutputFormatter" - {
    "coloured rendering applies semantic command styling" in {
      val formatter = OutputFormatter.coloured
      val rendered  = formatter.render(formatter.command("devenv generate"))

      rendered should not be "devenv generate"
      Str(rendered).plainText shouldBe "devenv generate"
    }

    "plain rendering removes semantic styling" in {
      val formatter = OutputFormatter.plain

      formatter.render(formatter.errorHeading("Generation failed")) shouldBe "Generation failed"
    }
  }

  "Output" - {
    "renders an initialization result as plain text" in {
      val result = InitResult(
        devcontainerStatus = FileSystemStatus.Created,
        userStatus = FileSystemStatus.AlreadyExists,
        sharedStatus = FileSystemStatus.Created,
        gitignoreStatus = GitignoreStatus.Updated,
        devenvStatus = FileSystemStatus.Created,
        readmeStatus = FileSystemStatus.AlreadyExists
      )

      val formatter = OutputFormatter.plain
      val message   = formatter.render(Output.initResultMessage(result)(using formatter))

      message should include("Initialization Summary:")
      message should include(".devcontainer/devenv.yaml")
      message should include("devenv generate")
      message should not include "\u001b["
    }

    "renders an error result with colour" in {
      val formatter = OutputFormatter.coloured
      val message   =
        formatter.render(
          Output.generateResultMessage(GenerateResult.NotInitialized)(using
            formatter
          )
        )

      Str(message).plainText should include("Project not initialized")
      message should include("\u001b[")
    }
  }
}
