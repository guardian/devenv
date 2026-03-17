#!/usr/bin/env bash
# Sets up tooling-related caching inside the devcontainer after creation.
set -euo pipefail

log()  { echo -e "\033[1;34m[setup] $*\033[0m"; }
warn() { echo -e "\033[1;33m[setup] $*\033[0m"; }
ok()   { echo -e "\033[1;32m[setup] $*\033[0m"; }

if [[ -z $MISE_DATA_DIR ]]; then
    warn "MISE_DATA_DIR not set"
    exit 1
fi

log "Setting up mise data."

log "Ensuring correct ownership of the shared mise data volume."
sudo chown -R vscode:vscode $MISE_DATA_DIR

ok "mise data complete."
