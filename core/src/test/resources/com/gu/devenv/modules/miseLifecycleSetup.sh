#!/usr/bin/env bash

test ! -d "$DEVENV_TEST_SHIMS_DIR"
mkdir -p "$DEVENV_TEST_SHIMS_DIR"
cat > "$DEVENV_TEST_SHIMS_DIR/devenv-test-tool" <<'SHIM'
#!/bin/sh
printf 'shim:%s:%s\n' "$1" "$PWD"
SHIM
chmod +x "$DEVENV_TEST_SHIMS_DIR/devenv-test-tool"

# A child's PATH change must not be needed by subsequent lifecycle commands.
export PATH="/child-only:$PATH"
printf 'bootstrap-finished\n'
