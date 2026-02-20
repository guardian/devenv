package com.gu.devenv.integration

import com.gu.devenv.modules.Modules.{Module, ModuleConfig, builtInModules}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

object IntegrationTestHelpers {

  /** A temporary directory that is cleaned up after the test. */
  val tempDir: TestResource[Path] = TestResource { use =>
    val dir = Files.createTempDirectory("devenv-test")
    try use(dir)
    finally deleteRecursively(dir)
  }

  /** A set of built-in modules with a unique mount key to avoid conflicts between tests. */
  val testModules: TestResource[List[Module]] = TestResource { use =>
    val mountKey = s"devenv-test-mount-${java.util.UUID.randomUUID()}"
    use(builtInModules(ModuleConfig(mountKey = mountKey)))
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.isDirectory(path)) {
      Files.list(path).iterator().asScala.foreach(deleteRecursively)
    }
    Files.delete(path)
  }

  // Test fixture data - Project configurations

  val basicProjectConfig: String =
    """|name: "My Test Project"
       |image: "mcr.microsoft.com/devcontainers/base:ubuntu"
       |modules: []
       |""".stripMargin

  val projectConfigWithPlugins: String =
    """|name: "Project With Plugins"
       |modules: []
       |plugins:
       |  vscode:
       |    - project-plugin-1
       |    - project-plugin-2
       |  intellij:
       |    - project-intellij-plugin
       |""".stripMargin

  val projectConfigWithMise: String =
    """|name: "Project With Mise"
       |modules:
       |  - mise
       |""".stripMargin

  val projectConfigWithMultipleModules: String =
    """|name: "Project With Multiple Modules"
       |modules:
       |  - mise
       |  - docker-in-docker
       |""".stripMargin

  val projectConfigWithModules: String =
    """|name: "Project With Modules"
       |modules:
       |  - mise
       |""".stripMargin

  val projectConfigWithUnknownModule: String =
    """|name: "Project With Unknown Module"
       |modules:
       |  - unknown-module
       |""".stripMargin

  val complexProjectConfig: String =
    """|name: "Complex Project"
       |image: "mcr.microsoft.com/devcontainers/base:ubuntu"
       |modules:
       |  - mise
       |  - docker-in-docker
       |plugins:
       |  vscode:
       |    - project-plugin-1
       |  intellij:
       |    - project-intellij-plugin
       |forwardPorts:
       |  - 3000
       |postCreateCommand:
       |  - cmd: "npm install"
       |    workingDirectory: "/workspaces/project"
       |""".stripMargin

  // Test fixture data - User configurations

  val userConfigWithPlugins: String =
    """|plugins:
       |  vscode:
       |    - user-plugin-1
       |    - user-plugin-2
       |  intellij:
       |    - user-intellij-plugin
       |""".stripMargin

  val userConfigWithDotfiles: String =
    """|plugins:
       |  vscode: []
       |  intellij: []
       |dotfiles:
       |  repository: "https://github.com/myuser/dotfiles"
       |  targetPath: "~/dotfiles"
       |  installCommand: "./install.sh"
       |""".stripMargin
}
