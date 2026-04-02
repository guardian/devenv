package com.gu.devenv.modules

import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MiseModuleTest
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with ScalaCheckPropertyChecks {
  "mise module" - {
    "should use the provided ModuleConfig to parameterise the data mount's name" in {
      val module = mise.success.value

      module.contribution.onCreateCommands should have size 0
      module.contribution.postCreateCommands should have size 1

      module.contribution.mounts should have size 0
    }
  }

  "builtInModules" - {
    "should use the provided module config to parameterise the mise module's mount" in {
      val moduleConfig = Modules.ModuleConfig("test-mount-key")
      val modules      = Modules.builtInModules(moduleConfig).success.value

      modules should contain(mise.success.value)
    }
  }
}
