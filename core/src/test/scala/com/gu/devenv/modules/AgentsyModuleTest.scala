package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{ModuleAdoption, ModuleConfig}
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class AgentsyModuleTest extends AnyFreeSpec with Matchers with TryValues {
  "agentsy module" - {
    "should be experimental and contribute one post-create command" in {
      val module = agentsy.success.value

      module.name shouldBe "agentsy"
      module.adoption shouldBe ModuleAdoption.Experimental
      module.contribution.onCreateCommands shouldBe empty
      module.contribution.postCreateCommands should have size 1
    }
  }

  "builtInModules" - {
    "should include the agentsy module" in {
      val modules = Modules.builtInModules(ModuleConfig("test-mount-key")).success.value

      modules should contain(agentsy.success.value)
    }
  }
}
