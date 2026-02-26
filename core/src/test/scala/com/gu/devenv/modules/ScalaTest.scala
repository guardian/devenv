package com.gu.devenv.modules

import com.gu.devenv.Mount
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ScalaTest extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {
  "scala module" - {
    "should use the provided ModuleConfig to parameterise all the data mount's name" in {
      val mountKey = "test-mount-key"
      val module   = scalaLang(mountKey)

      module.contribution.mounts should have size 2

      module.contribution.mounts.map {
        case Mount.ExplicitMount(source, _, _) =>
          source should include regex s"$mountKey-.*-data-volume"
        case Mount.ShortMount(mount) =>
          fail(
            s"Expected an ExplicitMount, but got ShortMount($mount). The ivy module should use an ExplicitMount for clarity."
          )
      }

      val sources = module.contribution.mounts.flatMap {
        case Mount.ExplicitMount(source, _, _) => Some(source)
        case _                                 => None
      }

      sources should contain(s"$mountKey-coursier-data-volume")
      sources should contain(s"$mountKey-ivy-data-volume")
    }
  }

  "builtInModules" - {
    "should use the provided module config to parameterise the ivy module's mount" in {
      val moduleConfig = Modules.ModuleConfig("test-mount-key")
      val modules      = Modules.builtInModules(moduleConfig)

      modules should contain(scalaLang(moduleConfig.mountKey))
    }
  }
}
