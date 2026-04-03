#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if which mise && mise --version; then
  log "mise is already present at $(which mise)."
else
  log "Installing mise..."
  curl -fsSL https://mise.run/bash | sh
fi

log "Checking mise is working correctly."
mise --version

log "Updating mise if needed."
mise self-update --yes || warn "Skipping mise self-update - you may be offline."

log "Trusting mise config (see: https://mise.jdx.dev/cli/trust.html)."
mise trust --yes || true

log "Installing mise tooling."
mise install || warn "mise install failed. You may need to run mise install manually inside the container."

eval "$(mise activate --shims bash)"

log "List installed tools."
mise list

log "Final checks."
mise doctor
