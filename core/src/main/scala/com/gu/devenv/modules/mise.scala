package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Env, Mount, Plugins}

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.io.Source
import scala.util.Using

/** Enables the mise tool within the development container.
  *
  * Mise is a tool for managing development environments, allowing developers to easily install and
  * switch between different versions of tools and dependencies required for their projects.
  *
  * See: https://mise.run/
  *
  * Automatically setting `mise` up to work in a devcontainer is a bit of a challenge so we handle
  * the complexity in a module. This allows teams to have a consistent development environment
  * without needing to manually install and configure `mise` each time.
  */
private[modules] def mise(mountKey: String) =
  Module(
    name = "mise",
    summary = "Install and configure mise for dev tools management (https://mise.jdx.dev/)",
    enabledByDefault = true,
    contribution = ModuleContribution(
      postCreateCommands = List(
        Command(
          cmd = s"echo '${base64Encoded("mise.bash")}' | base64 -d | bash",
          workingDirectory = "."
        )
      ),
      // Adds mise shims to the PATH so that installed tools are available in the remote environment
      remoteEnv = List(
        Env("PATH", "${containerEnv:PATH}:/mnt/mise-data/shims")
      ),
      // Sets the MISE_DATA_DIR to a shared volume (defined below) to cache downloaded tools and versions
      containerEnv = List(
        Env("MISE_DATA_DIR", "/mnt/mise-data"),
        Env("MISE_INSTALL_PATH", "/mnt/mise-data/mise")
      ),
      /* This mount persists the mise cache and installed tool versions across container recreations.
       *
       * This brings security trade-offs as the volume is shared between all containers using this module.
       * However, without this shared volume, mise would need to re-download and re-install tools
       * every time the container is recreated, which would be a significant usability issue.
       */
      mounts = List(
        Mount.ExplicitMount(
          source = s"$mountKey-mise-data-volume",
          target = "/mnt/mise-data",
          `type` = "volume"
        )
      ),
      // provide IDE support for mise
      plugins = Plugins(
        intellij = List("com.github.l34130.mise"),
        vscode = List("hverlin.mise-vscode")
      )
    )
  )

/** Loads a script from resource directory core/src/main/resources/com/gu/devenv/modules, encodes it
  * in base64 and returns the encoded string.
  *
  * This allows us to include a shell script in our project resources and execute it in the
  * container without needing to worry about escaping special characters or formatting issues that
  * can arise when embedding a script directly in the postCreateCommand.
  */
private def base64Encoded(scriptName: String): String = {
  val resource = s"com/gu/devenv/modules/$scriptName"
  Using(Source.fromResource(resource)) { source =>
    Base64.getEncoder.encodeToString(source.mkString.getBytes(UTF_8))
  }.fold(err => throw new RuntimeException(s"Could not load resource $resource", err), identity)
}
