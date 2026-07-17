package com.gu.devenv.modules

import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class GithubCopilotModuleTest
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with ScalaCheckPropertyChecks {
  "github copilot module" - {
    "should contribute a single postCreate command and no mounts or onCreate commands" in {
      val module = githubCopilot.success.value

      module.contribution.onCreateCommands should have size 0
      module.contribution.postCreateCommands should have size 1

      module.contribution.mounts should have size 0
    }

    "should provide IDE plugins for GitHub Copilot" in {
      val module = githubCopilot.success.value

      module.contribution.plugins.intellij should contain("GitHub.copilot")
      module.contribution.plugins.vscode should contain("com.github.copilot")
    }
  }

  "builtInModules" - {
    "should include the github copilot module" in {
      val moduleConfig = Modules.ModuleConfig("test-mount-key")
      val modules      = Modules.builtInModules(moduleConfig).success.value

      modules should contain(githubCopilot.success.value)
    }
  }
}

