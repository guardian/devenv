# Configuration Reference

This document provides detailed specifications for the project and user configuration files used by devenv.

## Overview

**Project config**: `.devcontainer/devenv.yaml` - Project-specific settings (image, ports, IDE plugins, commands). Checked into version control.

**User config**: `~/.config/devenv/devenv.yaml` - Personal preferences (dotfiles, additional IDE plugins). Merged with project config for the user-specific devcontainer.

Two devcontainer files are generated:
- `.devcontainer/user/devcontainer.json` - Merged config with your personal settings
- `.devcontainer/shared/devcontainer.json` - Project-only config for team use

Your user-specific file is excluded from the Git repository with a .gitignore entry. The general project file can be checked in to provide a project environment for cloud-based editors.

## Project Configuration Spec

The project config (`.devcontainer/devenv.yaml`) supports the following fields:

| Field                 | Description                                                                                     | Default                                       |
|-----------------------|-------------------------------------------------------------------------------------------------|-----------------------------------------------|
| `name`                | Project name, used as the devcontainer name (required)                                          | -                                             |
| `image`               | Base Docker image                                                                               | `mcr.microsoft.com/devcontainers/base:ubuntu` |
| `modules`             | List of module names to enable (see Modules section)                                            | `[]`                                          |
| `forwardPorts`        | Port forwarding config, either as integer (maps same port) or `"hostPort:containerPort"` string | `[]`                                          |
| `remoteEnv`           | Environment variables set in the remote/container environment                                   | `[]`                                          |
| `containerEnv`        | Environment variables set at container creation time                                            | `[]`                                          |
| `plugins`             | IDE plugins to install (`intellij`: list of plugin IDs, `vscode`: list of extension IDs)        | `{}`                                          |
| `mounts`              | Volume mounts, either as Docker mount strings or objects with `source`, `target`, and `type`    | `[]`                                          |
| `postCreateCommand`   | Commands to run once after container creation                                                   | `[]`                                          |
| `postStartCommand`    | Commands to run each time the container starts                                                  | `[]`                                          |
| `features`            | Dev Container features to enable (as key-value pairs)                                           | `{}`                                          |
| `remoteUser`          | User account to use in the container                                                            | `vscode`                                      |
| `updateRemoteUserUID` | Whether to update remote user's UID to match host                                               | `true`                                        |
| `capAdd`              | Linux capabilities to add to the container (use with caution)                                   | `[]`                                          |
| `securityOpt`         | Security options for the container (use with caution)                                           | `[]`                                          |

### Example

```yaml
name: my-project
image: mcr.microsoft.com/devcontainers/base:ubuntu
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

| Field      | Description                                                                            |
|------------|----------------------------------------------------------------------------------------|
| `plugins`  | Personal IDE plugins (same structure as project config: `intellij` and `vscode` lists) |
| `dotfiles` | Dotfiles repository configuration (see below)                                          |

### Dotfiles Configuration

The `dotfiles` field in the user config allows you to specify a GitHub repository containing your personal dotfiles to be cloned and installed into the devcontainer. All three fields are required.

| Field            | Description                                                 |
|------------------|-------------------------------------------------------------|
| `repository`     | GitHub repository (format: `username/repo`)                 |
| `targetPath`     | Path where dotfiles will be cloned in the container         |
| `installCommand` | Script to run for installation (executed from `targetPath`) |

### Example

```yaml
plugins:
  vscode:
    - usernamehw.errorlens
    - eamodio.gitlens
dotfiles:
  repository: "your-github-id/your-dotfiles-repo"
  targetPath: "~/dotfiles"
  installCommand: "install.sh"
```

## Modules

Modules are pre-configured bundles of features, plugins, and commands that can be enabled in your project config. They're included in the default `.devenv` template and can be disabled by commenting them out or removing them from the list.

### Available Modules

- **`mise`** - Installs and configures [mise](https://mise.jdx.dev/) for version management of languages and tools. Enabled by default.
- **`docker-in-docker`** - Enables running Docker containers within the devcontainer. Uses an isolated Docker daemon (not host socket) with minimal capabilities for better security. Disabled by default.
  - Image storage is ephemeral (lost on container rebuild)
  - Containers run inside the devcontainer, not directly on host network
  - Use `docker run -p 8080:8080` then access via devcontainer's forwarded ports
- **`scala`** - Adds IDE plugins for Scala development (Scala plugin for both VS Code and IntelliJ). Disabled by default.
- **`node`** - Adds IDE plugins for Node.js development (IntelliJ only; VS Code has built-in support). Disabled by default.

### Example

```yaml
# In .devcontainer/devenv.yaml
modules:
  - mise
  - scala  # Enable Scala IDE plugins
  # - node  # Enable Node.js IDE plugins (if needed)
  # - docker-in-docker  # Enable Docker-in-Docker (if needed)
```

To enable docker-in-docker or other modules, uncomment them:

```yaml
modules:
  - mise
  - scala
  # - node
  - docker-in-docker  # Now enabled
```

## Dotfiles

You can configure personal dotfiles in your user config (`~/.config/devenv/devenv.yaml`) to automatically clone and install them during container creation:

```yaml
dotfiles:
  repository: "your-github-id/your-dotfiles-repo"
  targetPath: "~/dotfiles"
  installCommand: "install.sh"
```

The dotfiles setup runs after project/container setup to avoid interfering with shared configuration. The repository is cloned into the container at the specified path, and the `installCommand` is executed from there.

