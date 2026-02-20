package com.gu.devenv.modules

import com.gu.devenv.Mount
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MiseTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {
  "mise module" - {
    "should use the provided ModuleConfig to parameterise the data mount's name" in {
      val mountKey = "test-mount-key"
      val module   = mise(mountKey)

      module.contribution.mounts should have size 1
      module.contribution.mounts.head match {
        case Mount.ExplicitMount(source, _, _) =>
          source shouldBe s"$mountKey-mise-data-volume"
        case Mount.ShortMount(mount) =>
          fail(
            s"Expected an ExplicitMount, but got ShortMount($mount). The mise module should use an ExplicitMount for clarity."
          )
      }
    }
  }

  "builtInModules" - {
    "should use the provided module config to parameterise the mise module's mount" in {
      val moduleConfig = Modules.ModuleConfig("test-mount-key")
      val modules      = Modules.builtInModules(moduleConfig)

      modules should contain(mise(moduleConfig.mountKey))
    }
  }
}
