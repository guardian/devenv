package com.gu.devenv

import com.gu.devenv.Filesystem.{FileSystemStatus, GitignoreStatus}
import fansi.Str
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.Success

class OutputFormatterTest extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks {

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

    "selects colour based on the terminal and environment" in {
      val cases = Table(
        ("interactive", "environment", "usesColour"),
        (true, Map.empty[String, String], true),
        (false, Map.empty[String, String], false),
        (true, Map("NO_COLOR" -> "1"), false),
        (true, Map("NO_COLOR" -> ""), true),
        (true, Map("TERM" -> "dumb"), false),
        (true, Map("TERM" -> "xterm-256color"), true)
      )

      forAll(cases) { (interactive, environment, usesColour) =>
        val formatter = OutputFormatter.select(interactive, environment)
        val rendered  = formatter.render(formatter.command("devenv check"))

        rendered.contains("\u001b[") shouldBe usesColour
      }
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

    "renders usage and version details as plain text" in {
      val formatter = OutputFormatter.plain
      val usage     = formatter.render(
        Output.usageMessage(
          release = "20260825-080000",
          architecture = Some("linux-x86_64"),
          branch = Some("main")
        )(using formatter)
      )
      val version = formatter.render(
        Output.versionMessage(
          release = "20260825-080000",
          architecture = Some("linux-x86_64"),
          branch = Some("main")
        )(using formatter)
      )

      usage should include("Usage: devenv <command> [--help]")
      usage should include("release   20260825-080000")
      version shouldBe "20260825-080000 (linux-x86_64) [main]"
    }

    "renders unknown commands as errors" in {
      val formatter = OutputFormatter.coloured
      val message   = formatter.render(Output.unknownCommandMessage("wat")(using formatter))

      Str(message).plainText shouldBe "Unknown command: wat"
      message should include("\u001b[")
    }

    "renders update results" in {
      val formatter = OutputFormatter.plain
      val message   = formatter.render(
        Output.updateCheckResultMessage(
          Success(Releases.UpdateCheckResult.UpToDate),
          currentVersion = "20260825-080000",
          architecture = Some("linux-x86_64")
        )(using formatter)
      )

      message should include("✅ Up-to-date")
      message should include("Devenv 20260825-080000 is the latest version.")
    }
  }
}
