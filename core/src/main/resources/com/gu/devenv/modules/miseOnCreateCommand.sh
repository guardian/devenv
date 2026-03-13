#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.
set -euo pipefail

log()  { echo -e "\033[1;34m[setup] $*\033[0m"; }
warn() { echo -e "\033[1;33m[setup] $*\033[0m"; }
ok()   { echo -e "\033[1;32m[setup] $*\033[0m"; }

log "Setting up mise data."

log "Ensuring correct ownership of the shared mise data volume."
sudo chown -R vscode:vscode /mnt/mise-data

ok "mise data complete."
