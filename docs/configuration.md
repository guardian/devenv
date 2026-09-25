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

| Field                 | Description                                                                                     | Default |
|-----------------------|-------------------------------------------------------------------------------------------------|---------|
| `name`                | Project name, used as the devcontainer name (required)                                          | -       |
| `modules`             | List of module names to enable (see Modules section)                                            | `[]`    |
| `forwardPorts`        | Port forwarding config, either as integer (maps same port) or `"hostPort:containerPort"` string | `[]`    |
| `remoteEnv`           | Environment variables set in the remote/container environment                                   | `[]`    |
| `containerEnv`        | Environment variables set at container creation time                                            | `[]`    |
| `plugins`             | IDE plugins to install (`intellij`: list of plugin IDs, `vscode`: list of extension IDs)        | `{}`    |
| `mounts`              | Volume mounts, either as Docker mount strings or objects with `source`, `target`, and `type`    | `[]`    |
| `postCreateCommand`   | Commands to run once after container creation                                                   | `[]`    |
| `postStartCommand`    | Commands to run each time the container starts                                                  | `[]`    |
| `features`            | Dev Container features to enable (as key-value pairs)                                           | `{}`    |
| `updateRemoteUserUID` | Whether to update remote user's UID to match host                                               | `true`  |
| `capAdd`              | Linux capabilities to add to the container (use with caution)                                   | `[]`    |
| `securityOpt`         | Security options for the container (use with caution)                                           | `[]`    |
| `runArgs`             | Extra switches for container generation                                                         | `[]`    |

Containers use the container runtime's default resource allocation unless the project explicitly sets resource
limits through `runArgs` or the escape hatch. With Docker Desktop, containers share the CPU and memory allocated
to Docker Desktop in its Resources settings.

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
```

## User Configuration Spec

The user config (`~/.config/devenv/devenv.yaml`) supports the following (optional) settings:

| Field           | Description                                                                            | Default |
|-----------------|----------------------------------------------------------------------------------------|---------|
| `plugins`       | Personal IDE plugins (same structure as project config: `intellij` and `vscode` lists) | []      |
| `dotfiles`      | Dotfiles repository configuration (see below)                                          | []      |

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
- **`agentsy`** (**Experimental**) - Clones [Agentsy](https://github.com/guardian/agentsy) from its public `main`
  branch to `~/agentsy` and links its command at `~/.local/bin/agentsy`. Checkouts with the expected Git remote are
  updated with a fast-forward-only pull when the container is rebuilt; other existing paths are left unchanged.
  Disabled by default and shown as experimental in generated configurations.

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
