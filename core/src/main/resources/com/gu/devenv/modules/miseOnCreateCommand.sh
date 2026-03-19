#!/usr/bin/env bash
# Sets up tooling-related caching inside the devcontainer after creation.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if [[ -z $DEVENV_MISE_CACHE_MOUNT_DIR ]]; then
    warn "DEVENV_MISE_CACHE_MOUNT_DIR not set"
    exit 1
fi

log "Ensuring correct ownership of the shared mise data volume."
sudo chown -R "$(whoami)":"$(whoami)" "$DEVENV_MISE_CACHE_MOUNT_DIR"