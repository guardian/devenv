package com.gu.devenv.integration

import cats.Apply

/** A composable test resource that manages setup and teardown.
  *
  * Compose multiple resources using `mapN`:
  * {{{
  * import cats.syntax.all.*
  *
  * (tempDir, tempDir, testModules).mapN { (projectDir, userConfigDir, modules) =>
  *   // test using all three resources
  * }
  * }}}
  */
case class TestResource[A](run: (A => Unit) => Unit)
object TestResource {
  given Apply[TestResource] = new Apply[TestResource] {
    def map[A, B](fa: TestResource[A])(f: A => B): TestResource[B] =
      TestResource(use => fa.run(a => use(f(a))))

    def ap[A, B](ff: TestResource[A => B])(fa: TestResource[A]): TestResource[B] =
      TestResource(use => ff.run(f => fa.run(a => use(f(a)))))
  }
}
