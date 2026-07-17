package com.gu.devenv

import com.gu.devenv.modules.Modules.ModuleResolutionError
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class OutputTest extends AnyFreeSpec with Matchers {
  "Output.generateResultMessage" - {
    "renders an invalid module configuration" in {
      val error  = ModuleResolutionError.UnknownModule("missing")
      val result = GenerateResult.InvalidModules(error)

      result.successful shouldBe false
      Output.generateResultMessage(result) should include(error.message)
    }
  }

  "Output.checkResultMessage" - {
    "renders an invalid module dependency" in {
      val error  = ModuleResolutionError.DependencyNotEnabled("github-copilot", "mise")
      val result = CheckResult.InvalidModules(error)

      result.successful shouldBe false
      Output.checkResultMessage(result) should include(error.message)
    }
  }
}
