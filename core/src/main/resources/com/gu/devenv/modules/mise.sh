#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.
set -euo pipefail

log()  { echo -e "\033[1;34m[setup] $*\033[0m"; }
warn() { echo -e "\033[1;33m[setup] $*\033[0m"; }
ok()   { echo -e "\033[1;32m[setup] $*\033[0m"; }

log "Setting up mise."

log "Ensuring correct ownership of the shared mise data volume."
sudo chown -R vscode:vscode /mnt/mise-data

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
export PATH="/mnt/mise-data/shims:$PATH"
mise doctor

ok "mise setup complete at $MISE_INSTALL_PATH."
