# Make mise tools available in lifecycle commands

Implement this in https://github.com/guardian/devenv, not in the consuming project.

## Problem

The mise module installs tools and activates mise inside a child process:

```sh
printf '%s' "<encoded script>" | base64 -d | bash -euo pipefail
```

Its PATH changes cannot reach the parent shell. Subsequent project commands such
as `npm install` therefore fail with `npm: not found`, despite successful tool
installation. `&&` is not the process boundary; the child Bash invocation is.

## Required behaviour

- When the mise module is enabled, make its shims available to all commands in
  both `postCreateCommand` and `postStartCommand` automatically.
- Prepend the shims directory to PATH in the outer lifecycle shell and export it
  so nested commands inherit it. Preserve the existing PATH.
- Resolve the directory for the container user at runtime. The default is
  `$HOME/.local/share/mise/shims`; respect any existing path configuration in devenv.
- Do not require new YAML options or per-command `mise exec` wrappers.
- Do not change behaviour when mise is disabled, or change `onCreateCommand`.
- Preserve command ordering, working directories, logging and error handling.

## Implementation guidance

Inspect the mise module contribution and lifecycle command renderer. Start with
`core/src/main/scala/com/gu/devenv/models.scala` (`Command`) and nearby module and
generation code; follow repository instructions and existing abstractions.

The PATH export must sit outside individual command subshells, not inside another
logged setup command. Putting the directory on PATH before it exists is fine;
do not require mise to be installed before its own installation step runs.
Check shell and JSON escaping so `$HOME` and `$PATH` expand inside the container,
not during generation. Keep Base64 script execution unchanged.

## Verification and delivery

- Add focused tests for both hooks with mise enabled and disabled.
- Exercise generated commands under a non-interactive shell with a controlled
  PATH that initially excludes shims. Verify a shim is found by a later command
  and its child process, including after a child setup script exits.
- Cover bootstrap ordering: setup can create the shims directory before a later
  command uses it. Prefer existing test helpers and deterministic local fixtures.
- Update relevant documentation and generated fixtures using repository tooling.
- Run focused tests and required repository checks; report results and limitations.

Do not fix unrelated startup-message quoting or change `npm install` to `npm ci`.