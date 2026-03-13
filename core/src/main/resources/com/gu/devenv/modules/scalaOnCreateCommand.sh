#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.
set -euo pipefail

log()  { echo -e "\033[1;34m[setup] $*\033[0m"; }
warn() { echo -e "\033[1;33m[setup] $*\033[0m"; }
ok()   { echo -e "\033[1;32m[setup] $*\033[0m"; }

log "Setting up scala cache data."

log "Ensuring correct ownership of the shared ivy data volume."
sudo chown -R vscode:vscode /home/vscode/.ivy2

log "Ensuring correct ownership of the shared coursier data volume."
sudo chown -R vscode:vscode /home/vscode/.cache

ok "scala cache data complete."