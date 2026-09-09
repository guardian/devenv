# devenv
[![GitHub release](https://img.shields.io/github/v/release/guardian/devenv)](https://github.com/guardian/devenv/releases)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

A CLI tool for managing [devcontainer](./docs/containerised-development/containerised-development.md) configurations for your projects.

## Quickstart

- [Onboarding a new project](#onboarding-a-new-project)
- [Opening your project in a devcontainer](#opening-your-project-in-a-devcontainer)

## About devenv

Writing devcontainer.json by hand is tricky, and the spec has no way to separate team-wide settings from personal preferences. `devenv` lets you define dev container configuration in simple YAML, then generates the devcontainer.json files for you.

For example, with this `.devcontainer/devenv.yaml` file in your project:

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
    - "GitHub.copilot"
  intellij:
    - "com.github.copilot"
    - "com.mallowigi"
```

devenv will generate two devcontainer.json files:
- `.devcontainer/shared/devcontainer.json` - with project settings only (checked into the repository)
- `.devcontainer/user/devcontainer.json` - project settings merged with your personal preferences (excluded via .gitignore)

You can then use your IDE (VSCode or IntelliJ) to launch into the `user` configuration for a fully personalized development environment, or the `shared` configuration for a standard project setup.

## Installation

It's best to pin a specific `devenv` version per project so that the checked-in devcontainer configuration stays in sync with both `.devcontainer/devenv.yaml` and the version of `devenv` that generated it.

### Recommended installation approach

Add `github:guardian/devenv <version>` to `.tool-versions` in the root of your project, then run `mise install` (or the equivalent command for your preferred tool manager).

Available versions can be listed with `mise ls-remote github:guardian/devenv`.

### Manual installation

> [!NOTE]
> This isn't recommended, because your project's generated configuration needs to stay in sync with the version of `devenv` that generated it.

Download the latest binary for your architecture from the [latest release](https://github.com/guardian/devenv/releases/latest) on GitHub and place it somewhere on your `PATH`. Each release includes its install commands, which will look like this:

```bash
curl -L --create-dirs -o ~/.local/bin/devenv <url-of-your-release-binary>
chmod +x ~/.local/bin/devenv
```

> **Note:** `~/.local/bin` is not on `PATH` by default on all systems. If `devenv` isn't found after installation, add it to your shell config (e.g. `export PATH="$HOME/.local/bin:$PATH"` in `~/.zshrc` or `~/.bashrc`), or install to `/usr/local/bin` instead.

## Usage

```
devenv <command>

Commands:
  init      Initialize .devcontainer directory structure
  generate  Generate devcontainer.json files from devenv config
  check     Ensure devcontainer.json files match current config

  version   Show devenv's version
  update    Check for updates to devenv's CLI

  help      Shows the help text
```

### Onboarding a new project

The typical workflow for setting up a project to use devenv is the following:

1. Add `github:guardian/devenv <version>` to `.tool-versions` in the root of your project, then run `mise install` (or the equivalent command for your preferred tool manager)
2. Run `devenv init` from the root of a repository to create the initial config file for your project
3. Edit the generated `.devcontainer/devenv.yaml` to set your project settings (see below for configuration details)
4. Optionally create a user config at `~/.config/devenv/devenv.yaml` to set your personal preferences (dotfiles, additional IDE plugins)
5. Run `devenv generate` to create the devcontainer.json files based on your config
6. Raise a PR to commit these changes to your repository

Here's an example PR: https://github.com/guardian/play-googleauth/pull/413

### Opening your project in a devcontainer

This assumes you (or someone else) have already set up the project, as described above.

1. Run `devenv generate` from the root of your project
2. Open the project in your IDE (VSCode or IntelliJ)
3. Use your IDE's devcontainer support to open the project using the config at `.devcontainer/user/devcontainer.json`

## Configuration

For detailed configuration specifications, including all supported fields, modules and the escape hatch, see the [Configuration Reference](docs/configuration.md).

## Development

### Contributing

#### Adding a module

See [docs/contributing/adding-a-module.md](docs/contributing/adding-a-module.md)
for a guide on implementing, testing and documenting a new built-in module.

### Release

The project uses a GitHub action to build and publish date-based releases that contain native binaries for macOS arm64 (m-series processors), Linux amd64 and Linux arm64.

New releases generate work, because teams need to update their `.tool-versions` entry and re-generate their devcontainer.json files. To avoid unnecessary churn, releases are only cut when there are significant changes.

#### Creating a release

> [!NOTE]
> The release workflow is triggered manually - it will not run automatically on pushes or merges to give full control over when to "cut" a release.

1. Go to the [Actions tab](https://github.com/guardian/devenv/actions/workflows/release.yml) on GitHub

2. Click "Run workflow" and select the branch to build from

3. GitHub Actions will automatically:
   - Build native binaries for macOS ARM64, Linux AMD64 and Linux ARM64
   - Sign and notarise the macOS binary with a Developer ID Application certificate
   - Create a **draft** GitHub Release with date-based versioning (e.g., `20251103-143022`)
   - Name the binaries as `devenv-{date-version}-{platform}` (e.g., `devenv-20251103-143022-macos-arm64`)
   - Mark the release as a prerelease, if it is built from a dev branch (not `main`)

4. **Manually verify and publish the release:**
   - Go to the [Releases page](https://github.com/guardian/devenv/releases) on GitHub
   - Review the draft release
   - Add (generated) release notes
   - Test the binaries if needed
   - Click "Publish release" when ready

#### Version management

Releases use date-based versioning: `YYYYMMDD-HHMMSS` (e.g., `20251103-143022`)

The version is embedded in the native binary at build time, so users can check their version with:

```bash
devenv version
```

They can also check for updates with:

```bash
devenv update
```

The update command checks [devenv's GitHub releases](https://github.com/guardian/devenv/releases) and gives the user instructions if a newer version is available.

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

### Packaging devenv locally

#### Native Image Build

Build a standalone native executable with GraalVM Native Image. The GraalVM dependency is included in `.tool-versions` so that it can be managed by `mise`. The `build-native-binary.sh` script sets up environment variables for version management and runs the native image build:

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

> [!NOTE]
> You can also run the native image build directly with `sbt`, but this will not configure the environment variables that set the version and architecture in the resulting binary:

```bash
sbt "cli/GraalVMNativeImage/packageBin"
```

The binary will be at `cli/target/graalvm-native-image/devenv`.

#### JVM Build

You can also create an executable JVM package. This is much faster than the native build, so it can be useful for a faster iteration cycle during development.

```bash
# Build and run locally
sbt cli/stage
cli/target/universal/stage/bin/devenv --help
```
