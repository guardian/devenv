#!/usr/bin/env bash
# Sets up Scala-related caches inside the devcontainer after creation.
set -euo pipefail

log() { echo -e "\033[1;34m[setup] $*\033[0m"; }

log "Setting up .ivy2 ..."
sudo chown -R vscode:vscode /home/vscode/.ivy2

log "Setting up coursier ..."
sudo chown -R vscode:vscode /home/vscode/.cache
