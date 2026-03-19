#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if [[ -z $MISE_DATA_DIR ]]; then
    warn "MISE_DATA_DIR not set"
    exit 1
fi

if test -f "$MISE_INSTALL_PATH"; then
  log "mise is already present at $MISE_INSTALL_PATH."
else
  log "Installing mise..."
  curl -fsSL https://mise.run/bash | sh
fi

log "Symlinking $MISE_INSTALL_PATH to /usr/local/bin/mise."
sudo ln -sf "$MISE_INSTALL_PATH" /usr/local/bin/mise

log "Checking mise is working correctly."
mise --version

log "Updating mise if needed."
mise self-update --yes || warn "Skipping mise self-update - you may be offline."

log "Trusting mise config (see: https://mise.jdx.dev/cli/trust.html)."
mise trust --yes || true

mise install || warn "mise install failed. You may need to run mise install manually inside the container."

log "Final checks."
export PATH="$MISE_DATA_DIR/shims:$PATH"
mise doctor