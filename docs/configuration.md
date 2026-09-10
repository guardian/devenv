# Configuration Reference

This document provides detailed specifications for the project and user configuration files used by devenv.

## Overview

**Project config**: `.devcontainer/devenv.yaml` - Project-specific settings (image, ports, IDE plugins, commands).
Checked into version control.

**User config**: `~/.config/devenv/devenv.yaml` - Personal preferences (dotfiles, additional IDE plugins). Merged with
project config for the user-specific devcontainer.

**Escape Hatch**: `.devcontainer/escapehatch.json` - Final overrides to allow projects to merge in devcontainer.json
content that devenv does not support.

Two devcontainer files are generated:

- `.devcontainer/user/devcontainer.json` - Merged config with your personal settings
- `.devcontainer/shared/devcontainer.json` - Project-only config for team use

Your user-specific file is excluded from the Git repository with a .gitignore entry. The general project file can be
checked in to provide a project environment for cloud-based editors.

## Project Configuration Spec

The project config (`.devcontainer/devenv.yaml`) supports the following fields:

| Field                 | Description                                                                                     | Default                   |
|-----------------------|-------------------------------------------------------------------------------------------------|---------------------------|
| `name`                | Project name, used as the devcontainer name (required)                                          | -                         |
| `modules`             | List of module names to enable (see Modules section)                                            | `[]`                      |
| `forwardPorts`        | Port forwarding config, either as integer (maps same port) or `"hostPort:containerPort"` string | `[]`                      |
| `remoteEnv`           | Environment variables set in the remote/container environment                                   | `[]`                      |
| `containerEnv`        | Environment variables set at container creation time                                            | `[]`                      |
| `plugins`             | IDE plugins to install (`intellij`: list of plugin IDs, `vscode`: list of extension IDs)        | `{}`                      |
| `mounts`              | Volume mounts, either as Docker mount strings or objects with `source`, `target`, and `type`    | `[]`                      |
| `postCreateCommand`   | Commands to run once after container creation                                                   | `[]`                      |
| `postStartCommand`    | Commands to run each time the container starts                                                  | `[]`                      |
| `features`            | Dev Container features to enable (as key-value pairs)                                           | `{}`                      |
| `updateRemoteUserUID` | Whether to update remote user's UID to match host                                               | `true`                    |
| `capAdd`              | Linux capabilities to add to the container (use with caution)                                   | `[]`                      |
| `securityOpt`         | Security options for the container (use with caution)                                           | `[]`                      |
| `containerSize`       | Sets the container size using a preset or an object. See [Container Size](#container-size).     | User's setting or `large` |
| `runArgs`             | Extra switches for container generation                                                         | `[]`                      |

### Example

```yaml
name: my-project
modules:
  - mise
forwardPorts:
  - 3000
  - "8080:80"
remoteEnv:
  - name: DEBUG
    value: "true"
plugins:
  intellij:
    - org.intellij.scala
  vscode:
    - scala-lang.scala
mounts:
  - "source=${localWorkspaceFolder}/.cache,target=/home/vscode/.cache,type=bind"
postCreateCommand:
  - cmd: "npm install"
    workingDirectory: "/workspaces/my-project"
containerSize:
  memory: "16g"
  cpus: 4
  shmSize: "512m"
```

## User Configuration Spec

The user config (`~/.config/devenv/devenv.yaml`) supports the following (optional) settings:

| Field           | Description                                                                                                    | Default |
|-----------------|----------------------------------------------------------------------------------------------------------------|---------|
| `plugins`       | Personal IDE plugins (same structure as project config: `intellij` and `vscode` lists)                         | []      |
| `dotfiles`      | Dotfiles repository configuration (see below)                                                                  | []      |
| `containerSize` | Sets the fallback container size when the project does not specify one. See [Container Size](#container-size). | `large` |

### Dotfiles Configuration

The `dotfiles` field allows you to specify a GitHub repository containing your personal dotfiles to be cloned and
installed into the devcontainer. All three fields are required.  
The dotfiles setup runs after project/container setup
to avoid interfering with shared configuration. The repository is cloned into the container at the specified path and
the `installCommand` is executed from there.

| Field            | Description                                                             |
|------------------|-------------------------------------------------------------------------|
| `repository`     | Full GitHub repository URL (eg. `https://github.com/username/dotfiles`) |
| `targetPath`     | Path where dotfiles will be cloned in the container                     |
| `installCommand` | Script to run for installation (executed from `targetPath`)             |

### Example

```yaml
plugins:
  vscode:
    - usernamehw.errorlens
    - eamodio.gitlens
dotfiles:
  repository: "https://github.com/username/dotfiles"
  targetPath: "~/dotfiles"
  installCommand: "./install.sh"
```

## Escape Hatch Spec

Literally any valid json. Devenv does not validate content, only structure. You could use this, for example, to
specify a different image and matching file:

```
{ 
  "image": "your special image",
  "remoteUser": "your special user"
}
```

Or to override with a build pointing at a local Dockerfile at `.devcontainer/user/Dockerfile`:

```
{
  "name": "My Dev Container",
  "build": {
    "dockerfile": "Dockerfile",
  },
  "remoteUser": "root"
}
```

That Dockerfile could then specify any base image and manipulate it as desired.

## Modules

Modules are pre-configured bundles of features, plugins, and commands that can be enabled in your project config.
They're included in the default `.devenv` template and can be disabled by commenting them out or removing them from the
list.

### Available Modules

- **`mise`** - Installs and configures [mise](https://mise.jdx.dev/) for version management of languages and tools.
  Enabled by default.
- **`github-copilot`** - Sets up [GitHub Copilot](https://github.com/features/copilot) for both IDE and CLI use. Adds
  the Copilot plugins for VS Code and IntelliJ and installs the GitHub CLI (`gh`) and GitHub Copilot CLI (`copilot`) via
  mise. Enabled by default. Requires the `mise` module.
- **`docker-in-docker`** - Enables running Docker containers within the devcontainer. Uses an isolated Docker daemon (
  not host socket) with minimal capabilities for better security. Disabled by default.
    - Image storage is ephemeral (lost on container rebuild)
    - Containers run inside the devcontainer, not directly on host network
    - Use `docker run -p 8080:8080` then access via devcontainer's forwarded ports
- **`scala`** - Adds IDE plugins for Scala development (Scala plugin for both VS Code and IntelliJ) _and_ mounts docker
  volumes to provide persistent ivy and coursier caches. Disabled by default.
- **`node`** - Adds IDE plugins for Node.js development (IntelliJ only; VS Code has built-in support). Disabled by
  default.

### Example

```yaml
# In .devcontainer/devenv.yaml
modules:
  - mise
  - github-copilot  # Sets up GitHub Copilot (IDE + CLI)
  - scala  # Enable Scala IDE plugins
  # - node  # Enable Node.js IDE plugins (if needed)
  # - docker-in-docker  # Enable Docker-in-Docker (if needed)
```

To enable docker-in-docker or other modules, uncomment them:

```yaml
modules:
  - mise
  - github-copilot
  - scala
  # - node
  - docker-in-docker  # Now enabled
```

### Adding a new module

See the [Adding a Module](contributing/adding-a-module.md) guide for a full walkthrough of how to
implement, test and document a new built-in module.

## Container Size

The optional `containerSize` field is supported in both the project configuration
(`.devcontainer/devenv.yaml`) and the user configuration (`~/.config/devenv/devenv.yaml`).
When present, its value must be `small`, `large`, or an object with all three resource fields.

For a custom size, supply all three fields:

```yaml
containerSize:
  memory: "15g"
  cpus: 2.5
  shmSize: "512m"
```

| Field     | Type   | Description                                                           |
|-----------|--------|-----------------------------------------------------------------------|
| `memory`  | String | Sets the container memory limit through `--memory`.                   |
| `cpus`    | Number | Sets the CPU limit through `--cpus`. Fractional values are supported. |
| `shmSize` | String | Sets the shared-memory size through `--shm-size`.                     |

Memory values use Docker's size format, such as `512m` for mebibytes or `15g` for gibibytes.
The example produces `--memory=15g --cpus=2.5 --shm-size=512m`.

To use a preset, specify its name:

```yaml
containerSize: small
```

The presets produce the following Docker run arguments:

| Preset  | Run arguments                           |
|---------|-----------------------------------------|
| `small` | `--memory=1g --cpus=1`                  |
| `large` | `--memory=16g --cpus=8 --shm-size=512m` |

The `small` preset does not set shared memory explicitly, so Docker uses its default.
The larger shared-memory allocation in the `large` preset is useful for workloads such as
Playwright tests running in Chrome.

### Container size configuration precedence

The project size takes precedence over the user size. If the project omits `containerSize`,
devenv uses the user setting. If both files omit it, devenv uses `large`.

Project authors can commit a resource requirement, such as 15 GB of memory, in the project's
`devenv.yaml`. Size settings from either configuration file only add run arguments to
`.devcontainer/user/devcontainer.json`, which is excluded from Git. They do not add resource
limits to `.devcontainer/shared/devcontainer.json`. This keeps the shared file suitable for
tools that read it from the repository, such as GitHub Codespaces, which may not support
large resource allocations.

Explicit project `runArgs` are preserved in both generated files. In the user file, they follow
the arguments derived from `containerSize`. Devenv does not remove duplicate flags or resolve
conflicts between them.

> [!NOTE]
> Escape-hatch configuration is applied last to both generated files. If it supplies `runArgs`,
> that array replaces the entire generated array, including size arguments and explicit project
> arguments.
