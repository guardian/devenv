# devenv

A CLI tool for managing devcontainer configurations for your projects.

Managing devcontainer.json files can be tedious and error-prone. The devcontainer specification is very detailed, exposing a lot of options to engineers. It also doesn't provide a way to separate team-wide project settings from personal user preferences, forcing developers to either commit their personal configs or manually maintain separate files. `devenv` solves both of these problems by providing a simple configuration format that lets you define both project-wide and user-specific settings in YAML format. It then generates the appropriate devcontainer.json files for you.

For example, this `.devcontainer/devenv.yaml` file in your project:

```yaml
name: "devenv"
modules:  # Pre-configured bundles (mise for version management, scala/node for IDE plugins)
  - mise
  - scala
```

and this user config file in your home directory at `~/.config/devenv/devenv.yaml`:

```yaml
dotfiles:
  repository: https://github.com/username/dotfiles.git
  targetPath: ~/.dotfiles
  installCommand: ./install.sh
plugins:
  vscode:
    - "com.github.copilot"
  intellij:
    - "GitHub.copilot"
    - "com.mallowigi"
```

will generate two devcontainer.json files:
- `.devcontainer/shared/devcontainer.json` - with project settings only (checked into the repository)
- `.devcontainer/user/devcontainer.json` - project settings merged with your personal preferences (excluded via .gitignore entry)

You can then use your IDE (VSCode or IntelliJ) to launch into the `user` configuration for a fully personalized development environment, or the `shared` configuration for a standard project setup. The latter ensures that cloud-based IDEs like GitHub Codespaces can use the (checked-in) shared configuration to provide a simple and consistent development environment.

## Installation

Download the latest binary for your architecture from the [latest release](https://github.com/guardian/devenv/releases/latest) on GitHub and place it somewhere on your `PATH`. Each release includes its install commands, which will look like this:

```bash
curl -L --create-dirs -o ~/.local/bin/devenv <url-of-your-release-binary>
chmod +x ~/.local/bin/devenv
```

> **Note:** `~/.local/bin` is not on `PATH` by default on all systems. If `devenv` isn't found after installation, add it to your shell config (e.g. `export PATH="$HOME/.local/bin:$PATH"` in `~/.zshrc` or `~/.bashrc`), or install to `/usr/local/bin` instead.

## Quickstart

From the root of your project, run:

```bash
devenv init
```

This will create a `.devcontainer/devenv.yaml` file with some default settings. Set the project name and any other project options in this file.

You can also create a user config file at `~/.config/devenv/devenv.yaml` to set your personal preferences (dotfiles, additional IDE plugins, etc).

Then run:

```bash
devenv generate
```

This will generate two devcontainer.json files:
- `.devcontainer/shared/devcontainer.json` - Project-wide settings (checked-in)
- `.devcontainer/user/devcontainer.json` - Merged project and user settings (not checked-in)

You can use these to launch devcontainers in your IDE.

## Build

### JVM Build

```bash
# Build and run locally
sbt cli/stage
cli/target/universal/stage/bin/devenv
```

### Native Image Build

Build a standalone native executable with GraalVM Native Image. The GraalVM dependency is included in `.tool-versions` so that it can be managed by `mise`. The `build-native-binary.sh` script sets up environment variables for version management and runs the native image build:

```bash
./scripts/build-native-binary.sh
```

You can also run the native image build directly with sbt, but this will not configure the versioning environment variables that are used to set the version and architecture in the resulting binary:

```bash
sbt "cli/GraalVMNativeImage/packageBin"
```

The resulting binary will be at `cli/target/graalvm-native-image/devenv`.

## Usage

```
devenv <command>

Commands:
  init      Initialize .devcontainer directory structure
  generate  Generate devcontainer.json files from .devenv config
  check     Ensure devcontainer.json files match current config

  version   Show devenv's version
  update    Check for updates to devenv's CLI

  help      Shows the help text
```

Typical workflow:

1. Run `devenv init` from the root of a repository to create the initial config file for your project
2. Edit the generated `.devcontainer/devenv.yaml` to set your project settings (see below for configuration details)
3. Optionally create a user config at `~/.config/devenv/devenv.yaml` to set your personal preferences (dotfiles, additional IDE plugins)
4. Run `devenv generate` to create the devcontainer.json files based on your config
5. Open the project in your IDE (VSCode or IntelliJ) and select the appropriate devcontainer configuration (`user` for your personalised environment, `shared` for the standard project setup)

You can also run `devenv check` to verify that the generated devcontainer.json files are up to date with your current config. This is useful locally and in CI, to make sure your project's devcontainer configuration is up to date.

## Configuration

**Project config**: `.devcontainer/devenv.yaml` - Project-specific settings (name, ports, IDE plugins, commands, etc). Checked into version control.

**User config**: `~/.config/devenv/devenv.yaml` - Personal preferences (dotfiles, additional IDE plugins). Merged with project config for the user-specific devcontainer.

Two devcontainer files are generated:
- `.devcontainer/user/devcontainer.json` - Merged config with your personal settings
- `.devcontainer/shared/devcontainer.json` - Project-only config for team use

Your user-specific file is excluded from the Git repository with a .gitignore entry. The general project file can be checked in to provide a project environment for cloud-based editors.

For detailed configuration specifications, see the [Configuration Reference](docs/configuration.md).

## Development

The [.tool-versions](./.tool-versions) file includes toolchain dependencies that can be managed by [mise](https://mise.jdx.dev/) or your preferred version manager.

### Testing

#### Unit and Integration Tests

Use sbt to run the unit and integration tests:

```bash
sbt test
```

#### Docker Integration Tests

Docker tests validate modules by creating real Docker containers and verifying configuration. **Docker must be running** to execute these tests. See [Docker Testing Documentation](docs/docker-testing.md) for details.

#### Generation Tests

The project also includes generation tests that validate the real program output. These package the CLI in dev/universal mode with `sbt cli/stage`, run the program against isolated temp directories, and validate the JSON output and file structure to ensure the CLI behaves correctly in real-world scenarios.

```bash
./generation-tests/run-tests.sh
```

### Release

The project uses a GitHub action to build and publish date-based releases that contain native binaries for macOS arm64 (m-series processors), Linux amd64 and Linux arm64.

#### Creating a release

1. Go to the [Actions tab](https://github.com/guardian/devenv/actions/workflows/release.yml) on GitHub

2. Click "Run workflow" and select the branch to build from

3. GitHub Actions will automatically:
    - Build native binaries for macOS ARM64 and Linux AMD64
    - Sign and notarise the macOS binary with a Developer ID Application certificate
    - Create a **draft** GitHub Release with date-based versioning (e.g., `20251103-143022`)
    - Name the binaries as `devenv-{date-version}-{platform}` (e.g., `devenv-20251103-143022-macos-arm64`)
    - Mark the release as a prerelease

4. **Manually verify and publish the release:**
    - Go to the [Releases page](https://github.com/guardian/devenv/releases) on GitHub
    - Review the draft release
    - Add (generated) release notes
    - Test the binaries if needed
    - Click "Publish release" when ready

#### Building locally

To build a native binary locally, use the `build-native-binary.sh` script. This is useful for testing the native build on your own machine outside of CI.

```bash
./scripts/build-native-binary.sh
```

The release version defaults to the current timestamp, but can be specified explicitly if needed:

```bash
./scripts/build-native-binary.sh 20251103-143022
```

The script will:
- Set the `DEVENV_RELEASE` environment variable to the specified version (or current timestamp if omitted)
- Append `-dev` to the version if building from a branch other than `main`
- Set the `DEVENV_ARCHITECTURE` environment variable (auto-detected or specified)
- Set the `DEVENV_BRANCH` environment variable (auto-detected from git or specified)
- Display the build configuration and prompt for confirmation
- Build a native binary with GraalVM Native Image

The resulting binary will be at `cli/target/graalvm-native-image/devenv`.

Architectures the script can detect:
- `macos-arm64` (Apple Silicon)
- `macos-amd64` (Intel Mac)
- `linux-arm64` (ARM Linux)
- `linux-amd64` (x86_64 Linux)

#### Version management

Releases use date-based versioning: `YYYYMMDD-HHMMSS` (e.g., `20251103-143022`)

**Note:** The release workflow is triggered manually - it will not run automatically on pushes or merges to give full control over when to "cut" a release.

The version is embedded in the native binary at build time, so users can check their version with:

```bash
devenv version
```

They can also check for updates with:

```bash
devenv update
```

The update command checks the GitHub releases for a newer version and prompts the user to download it if available.
