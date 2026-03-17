#!/usr/bin/env bash
# Sets up JVM-related caches inside the devcontainer after creation.

error() { echo -e "\033[1;31m[setup] $*\033[0m"; }  # red
ok()    { echo -e "\033[1;32m[setup] $*\033[0m"; }  # green
warn()  { echo -e "\033[1;33m[setup] $*\033[0m"; }  # gold
log()   { echo -e "\033[1;36m[setup] $*\033[0m"; }  # blue

if [[ -z $IVY_DATA_DIR_ROOT ]]; then
    warn "IVY_DATA_DIR_ROOT not set"
    exit 1
fi

if [[ -z $COURSIER_DATA_DIR_ROOT ]]; then
    warn "COURSIER_DATA_DIR_ROOT not set"
    exit 1
fi

log "Ensuring correct ownership of the shared ivy data volume."
sudo chown -R vscode:vscode "$IVY_DATA_DIR_ROOT"

log "Ensuring correct ownership of the shared coursier data volume."
sudo chown -R vscode:vscode "$COURSIER_DATA_DIR_ROOT"