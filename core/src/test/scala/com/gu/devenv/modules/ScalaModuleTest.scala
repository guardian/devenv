package com.gu.devenv.modules

import com.gu.devenv.Mount
import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ScalaModuleTest
    extends AnyFreeSpec
    with Matchers
    with TryValues
    with ScalaCheckPropertyChecks {
  "scala module" - {
    "should use the provided ModuleConfig to parameterise all the data mount's name" in {
      val mountKey = "test-mount-key"
      val module   = scalaLang(mountKey).success.value

      module.contribution.onCreateCommands should have size 1
      module.contribution.postCreateCommands should have size 0

      module.contribution.mounts should have size 2

      module.contribution.mounts.map {
        case Mount.ExplicitMount(source, _, _) =>
          source should include regex s"$mountKey-.*-cache-volume"
        case Mount.ShortMount(mount) =>
          fail(
            s"Expected an ExplicitMount, but got ShortMount($mount). The scala module should use an ExplicitMount for all caches for clarity."
          )
      }

      val sources = module.contribution.mounts.flatMap {
        case Mount.ExplicitMount(source, _, _) => Some(source)
        case _                                 => None
      }

      sources should contain(s"$mountKey-coursier-cache-volume")
      sources should contain(s"$mountKey-ivy-cache-volume")
    }
  }

  "builtInModules" - {
    "should use the provided module config to parameterise the cache's mount" in {
      val moduleConfig = Modules.ModuleConfig("test-mount-key")
      val modules      = Modules.builtInModules(moduleConfig).success.value

      modules should contain(scalaLang(moduleConfig.mountKey).success.value)
    }
  }
}
