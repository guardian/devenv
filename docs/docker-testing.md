# Docker Integration Testing

The docker test suite validates devenv's module system by creating real Docker containers and verifying that modules correctly configure the container environment.

## Requirements

Docker must be running on your machine before executing docker tests. The test suite checks Docker availability during initialization and will fail if Docker is unavailable.

This repository's devcontainer enables the `docker-in-docker` module so these tests can
run inside it. Rebuild the devcontainer after updating its configuration, then check
`docker info` before running `sbt docker/test`. Regenerating the configuration alone
does not install Docker in an already-running container.

## Running Tests

These tests can be quite slow to run since they involve building and starting Docker containers. You can choose to run all tests, specific module tests, or skip the slow container tests.

```bash
# Run all tests (including docker)
sbt test

# Run only the docker tests
sbt docker/test

# Skip slow container tests
sbt "docker/testOnly -- -l com.gu.devenv.docker.ContainerTest"

# Run tests for a specific devenv module
sbt "docker/testOnly com.gu.devenv.docker.MiseModuleTest"
```

## Test Structure

Each test:

1. Copies a fixture directory to a temporary workspace
2. Runs `devenv generate` to create devcontainer.json in that workspace
3. Launches a container using the generated configuration
4. Executes verification commands inside the container
5. Cleans up the container and temporary workspace

Test fixtures are located in [the docker project's resources](../docker/src/test/resources/fixtures/), each containing a minimal `.devcontainer/devenv.yaml` configuration for testing specific modules.

Since the [combined modules test](../docker/src/test/scala/com/gu/devenv/docker/CombinedModulesTest.scala) reuses the verifiers from individual module tests, the checks are located in [the verifiers package](../docker/src/test/scala/com/gu/devenv/docker/verifiers).

## Debugging Module Tests

[MiseModuleTest](../docker/src/test/scala/com/gu/devenv/docker/MiseModuleTest.scala) includes a debug test for troubleshooting container setup issues. The debug test is normally disabled (marked with `ignore` instead of `in`) to prevent it from cluttering the test output.

To enable it, change this swap `ignore` with `in` for `"debug" taggedAs ContainerTest ignore {` and run the test. Remember to change it back before committing.
