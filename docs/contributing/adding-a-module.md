# Adding a Module

This guide walks through everything required to add a new built-in module to devenv.

A module is a named, opt-in (or default-on) bundle of devcontainer contributions. Each module can
contribute any combination of IDE plugins, Docker volume mounts, environment variables, Dev Container
features and lifecycle shell commands. Users enable modules by name in their `.devcontainer/devenv.yaml`.

## Anatomy of a module

A module is represented by the `Module` case class (defined in `Modules.scala`):

```scala
case class Module(
    name: String,           // the string users write in devenv.yaml, e.g. "scala"
    summary: String,        // one-line description shown in help output
    enabledByDefault: Boolean,
    contribution: ModuleContribution
)
```

`ModuleContribution` holds everything the module injects into the generated `devcontainer.json`:

| Field                | Type                  | Purpose                                           |
|----------------------|-----------------------|---------------------------------------------------|
| `features`           | `Map[String, Json]`   | Dev Container features (e.g. docker-in-docker)    |
| `mounts`             | `List[Mount]`         | Docker volume or bind mounts                      |
| `plugins`            | `Plugins`             | Intellij and VS Code plugin/extension IDs         |
| `containerEnv`       | `List[Env]`           | Env vars set at container creation time           |
| `remoteEnv`          | `List[Env]`           | Env vars set in the running container environment |
| `onCreateCommands`   | `List[Command]`       | Scripts run once after container creation         |
| `postCreateCommands` | `List[Command]`       | Scripts run after each container rebuild          |
| `capAdd`             | `List[String]`        | Linux capabilities to add (use with caution)      |
| `securityOpt`        | `List[String]`        | Docker security options (use with caution)        |

Module contributions are **prepended** to any explicit config from `devenv.yaml` so user-supplied
values always take precedence.

There are currently three implementation patterns in use, in increasing order of complexity:

| Pattern                         | Example          | When to use                                      |
|---------------------------------|------------------|--------------------------------------------------|
| Plain `val`                     | `node.scala`     | Plugins only; no script, no dynamic config       |
| `def` returning `Try[Module]`   | `mise.scala`     | Requires a shell script loaded from resources    |
| `def` with mounts + env vars    | `scala.scala`    | Script plus parameterised mounts and env vars    |

---

## Step 1: Create the Scala source file

Create `core/src/main/scala/com/gu/devenv/modules/<name>.scala`.

Use `private[modules]` visibility so the module is only directly accessible within the `modules` package.

### Choosing `enabledByDefault`

Set `enabledByDefault = true` only for modules that are broadly useful to all projects (for example
`mise`, which most projects will want). For opt-in tools (language runtimes, specialised plugins,
etc.) use `enabledByDefault = false`.

This value has a direct, visible effect on users: when they run `devenv init`, the tool generates a
`devenv.yaml` template that lists every registered module. Modules with `enabledByDefault = true`
appear as active entries; modules with `enabledByDefault = false` appear commented out so users know
they exist but must opt in explicitly:

```yaml
# Modules: Built-in functionality
# - my-tool: Set up My Tool inside the devcontainer
# To disable, comment out or remove items from the list below
modules:
  # - my-tool  # (disabled by default)
```

The template is generated dynamically from the registered
module list every time `devenv init` is run, so registering a module ([Step 3](#step-3-register-the-module)) is all that is needed
to include it in the template.

---

**Pattern A -- plain `val` (no script):**

```scala
package com.gu.devenv.modules

import com.gu.devenv.Plugins
import com.gu.devenv.modules.Modules.{Module, ModuleContribution}

/** One-line description of what this module provides.
  *
  * Longer explanation if useful. Link to any relevant external tool.
  */
private[modules] val myTool = Module(
  name = "my-tool",
  summary = "Add IDE plugins for My Tool development",
  enabledByDefault = false,
  contribution = ModuleContribution(
    plugins = Plugins(
      intellij = List("com.example.mytool"),
      vscode = List("example.my-tool")
    )
  )
)
```

**Pattern B -- `def` returning `Try[Module]` (with script):**

```scala
package com.gu.devenv.modules

import com.gu.devenv.modules.Modules.{Module, ModuleContribution}
import com.gu.devenv.{Command, Env, Mount, Plugins}

import scala.util.Try

/** One-line description of what this module provides.
  *
  * Longer explanation if useful. Link to any relevant external tool.
  */
private[modules] def myTool: Try[Module] =
  for {
    script <- Command.fromResourceScript("<name><lifecycleStage>.sh")
  } yield Module(
    name = "my-tool",
    summary = "Set up My Tool inside the devcontainer",
    enabledByDefault = false,
    contribution = ModuleContribution(
      postCreateCommands = List(script)
    )
  )
```

---

## Step 2: Add a shell script (if needed)

If the module needs to run setup logic inside the container, create a shell script at:

```
core/src/main/resources/com/gu/devenv/modules/<name><lifecycleStage>Command.sh
```

Load it in the module definition with:

```scala
Command.fromResourceScript("<name><lifecycleStage>Command.sh")
```

This base64-encodes the script for portability; it is decoded and piped to bash at container
creation time:

```
printf <encoded> | base64 -d | bash
```

Scripts run under `bash -euo pipefail`.  Any non-zero exit terminates the script and is
reported as an error but does **not** stop the container.

Follow the scripting conventions documented in
[`modules/README.md`](../../core/src/main/resources/com/gu/devenv/modules/README.md):

- Use the `DEVENV_*` naming convention for all environment variables.
- Expose required env vars to the script via `containerEnv` in the module's `ModuleContribution`.
- Write log output to `/var/log/` for post-hoc inspection.
- Use the colour helper pattern (`log`, `ok`, `warn`, `error`) established in the existing scripts.

---

## Step 3: Register the module

Add the new module to the `builtInModules` function in
[Modules.scala](../../core/src/main/scala/com/gu/devenv/modules/Modules.scala).

If the module is a plain `val`, append it to the returned list:

```scala
def builtInModules(moduleConfig: ModuleConfig): Try[List[Module]] =
  for {
    miseModule  <- mise
    scalaModule <- scalaLang(moduleConfig.mountKey)
  } yield List(
    miseModule,
    dockerInDocker,
    scalaModule,
    nodeLang,
    myTool       // add here
  )
```

If the module returns a `Try[Module]`, add a for-comprehension binding and include it in the list:

```scala
def builtInModules(moduleConfig: ModuleConfig): Try[List[Module]] =
  for {
    miseModule   <- mise
    scalaModule  <- scalaLang(moduleConfig.mountKey)
    myToolModule <- myTool     // bind here
  } yield List(
    miseModule,
    dockerInDocker,
    scalaModule,
    nodeLang,
    myToolModule               // and include here
  )
```

---

## Step 4: Write unit tests

Create `core/src/test/scala/com/gu/devenv/modules/<Name>ModuleTest.scala`.

Use the same style as [`MiseModuleTest`](../../core/src/test/scala/com/gu/devenv/modules/MiseModuleTest.scala) 
and [`ScalaModuleTest`](../../core/src/test/scala/com/gu/devenv/modules/ScalaModuleTest.scala):

```scala
package com.gu.devenv.modules

import org.scalatest.TryValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class MyToolModuleTest extends AnyFreeSpec with Matchers with TryValues {

  "my-tool module" - {
    "should construct successfully" in {
      val module = myTool.success.value
      module.name shouldBe "my-tool"
    }

    "should register one postCreateCommand and no onCreateCommands" in {
      val module = myTool.success.value
      module.contribution.onCreateCommands  should have size 0
      module.contribution.postCreateCommands should have size 1
    }

    // Add assertions for mounts, env vars, plugins, etc. as appropriate
  }

  "builtInModules" - {
    "should include the my-tool module" in {
      val moduleConfig = Modules.ModuleConfig("test-mount-key")
      val modules      = Modules.builtInModules(moduleConfig).success.value
      modules.map(_.name) should contain("my-tool")
    }
  }
}
```

The minimum set of assertions to include:

- Module constructs without error (`.success.value`).
- Correct number of `onCreateCommands` and `postCreateCommands`.
- Correct number of `mounts`; for parameterised mounts, assert the `source` pattern includes the mount key.
- Any required `containerEnv` entries are present.
- Module appears in `Modules.builtInModules(...)`.

---

## Step 5: Document the module for users

Add an entry to the [Available Modules list](../configuration.md#available-modules).  
Follow the style of the existing entries: module name, a description of what it
does, whether it is enabled by default and any notable caveats.

---

## Step 6: Smoke-test via the JVM stage build

Build and run the CLI in JVM mode:

```bash
sbt cli/stage
```

### 6a: Verify the init template

Run `devenv init` in a temporary directory and inspect the generated `devenv.yaml`:

```bash
mkdir /tmp/devenv-smoke && cd /tmp/devenv-smoke
<path-to-repo>/cli/target/universal/stage/bin/devenv init
cat .devcontainer/devenv.yaml
```

Confirm that your new module appears in the file. If `enabledByDefault = false`, it should appear
as a commented-out entry with the `(disabled by default)` annotation. If `enabledByDefault = true`,
it should appear as an active, uncommented entry.

### 6b: Verify the generated devcontainer output

Create or update the `devenv.yaml` to include your module:

```yaml
name: smoke-test
modules:
  - my-tool
```

Run the generate command:

```bash
cli/target/universal/stage/bin/devenv generate
```

Inspect the generated `.devcontainer/shared/devcontainer.json` and verify it contains the plugins,
features, mounts and env vars the module is expected to contribute.

See the [JVM build documentation](../../README.md#jvm-build) for further info.

---

## Step 7: Smoke-test via the GraalVM native build

Build the native binary:

```bash
./scripts/build-native-binary.sh
```

The resulting binary is at `cli/target/graalvm-native-image/devenv`. Repeat both sub-steps from
Step 6 (init template check and generate check) using this binary.

Native image compilation is the most important smoke test for modules that load scripts via
`Command.fromResourceScript` because GraalVM must be able to locate the classpath resource at
compile time. A failure here typically means the resource path is incorrect or the resource is not
on the native image classpath.

See the [Native image build documentation](../../README.md#native-image-build) for further info.

---

## Checklist

| Step | Task                                                                                                |
|------|-----------------------------------------------------------------------------------------------------|
| 1    | Create `core/src/main/scala/com/gu/devenv/modules/<name>.scala`; choose `enabledByDefault`         |
| 2    | Create `core/src/main/resources/com/gu/devenv/modules/<name><lifecycleStage>Command.sh` (if needed) |
| 3    | Register in [Modules.builtInModules](../../core/src/main/scala/com/gu/devenv/modules/Modules.scala) |
| 4    | Add unit tests in `core/src/test/scala/com/gu/devenv/modules/<Name>ModuleTest.scala`                |
| 5    | Add user-facing entry to [docs/configuration.md](../../docs/configuration.md)                       |
| 6a   | Run `devenv init` with the JVM binary and confirm the module appears correctly in `devenv.yaml`     |
| 6b   | Run `devenv generate` with the JVM binary and confirm the generated `devcontainer.json` is correct  |
| 7    | Repeat steps 6a and 6b with the native binary from `./scripts/build-native-binary.sh`              |
