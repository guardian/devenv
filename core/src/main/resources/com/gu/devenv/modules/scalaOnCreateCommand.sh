#!/usr/bin/env bash
# Sets up JVM-related caches inside the devcontainer after creation.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

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