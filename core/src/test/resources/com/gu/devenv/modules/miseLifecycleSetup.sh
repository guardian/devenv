#!/usr/bin/env bash

test "$PATH" = "$DEVENV_TEST_INITIAL_PATH"
printf 'setup-path:%s\n' "$PATH"
test ! -d "$DEVENV_TEST_SHIMS_DIR"
mkdir -p "$DEVENV_TEST_SHIMS_DIR" "$DEVENV_TEST_MISE_BIN_DIR"
cat > "$DEVENV_TEST_SHIMS_DIR/devenv-test-tool" <<'SHIM'
#!/bin/sh
printf 'shim:%s:%s\n' "$1" "$PWD"
SHIM
chmod +x "$DEVENV_TEST_SHIMS_DIR/devenv-test-tool"

cat > "$DEVENV_TEST_MISE_BIN_DIR/mise" <<'MISE'
#!/bin/sh
test "$*" = "activate --shims bash" || exit 2
printf 'activation-called\n' >> "$HOME/activations"
if [ "${DEVENV_TEST_ACTIVATION_FAIL:-}" = 1 ]; then
  printf 'activation-failed\n' >&2
  printf 'export PATH=/must-not-be-evaluated\n'
  exit 9
fi
printf 'export PATH="$DEVENV_TEST_SHIMS_DIR:$DEVENV_TEST_MISE_BIN_DIR:$PATH"\n'
MISE
chmod +x "$DEVENV_TEST_MISE_BIN_DIR/mise"

if [ "${DEVENV_TEST_SETUP_FAIL:-}" = 1 ]; then
  printf 'setup-failed\n' >&2
  exit 8
fi

# A child's PATH change must not be needed by subsequent lifecycle commands.
export PATH="/child-only:$PATH"
printf 'bootstrap-finished\n'
