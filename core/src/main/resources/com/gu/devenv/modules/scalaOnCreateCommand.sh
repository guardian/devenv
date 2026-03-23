#!/usr/bin/env bash
# Sets up JVM-related caches inside the devcontainer after creation.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if [[ -z $DEVENV_IVY_CACHE_MOUNT_DIR ]]; then
    warn "DEVENV_IVY_CACHE_MOUNT_DIR not set"
    exit 1
fi

if [[ -z $DEVENV_COURSIER_CACHE_MOUNT_DIR ]]; then
    warn "DEVENV_COURSIER_CACHE_MOUNT_DIR not set"
    exit 1
fi

DEVENV_IVY_USER_DIR="/home/$(whoami)/.ivy2"
DEVENV_IVY_CACHE_MOUNT_LINK="$DEVENV_IVY_USER_DIR/cache"

log "Link the shared ivy data volume at the correct point."
mkdir -p "$DEVENV_IVY_CACHE_MOUNT_LINK"
rmdir "$DEVENV_IVY_CACHE_MOUNT_LINK"
ln -sf "$DEVENV_IVY_CACHE_MOUNT_DIR" "$DEVENV_IVY_CACHE_MOUNT_LINK"

log "Ensuring correct ownership of the shared ivy data volume."
sudo chown -R "$(whoami)":"$(whoami)" "$DEVENV_IVY_USER_DIR"

DEVENV_COURSIER_USER_DIR="/home/$(whoami)/.cache"
DEVENV_COURSIER_CACHE_MOUNT_LINK="$DEVENV_COURSIER_USER_DIR/coursier/v1"

log "Link the shared coursier data volume at the correct point."
mkdir -p "$DEVENV_COURSIER_CACHE_MOUNT_LINK"
rmdir "$DEVENV_COURSIER_CACHE_MOUNT_LINK"
ln -sf "$DEVENV_COURSIER_CACHE_MOUNT_DIR" "$DEVENV_COURSIER_CACHE_MOUNT_LINK"

log "Ensuring correct ownership of the shared coursier data volume."
sudo chown -R "$(whoami)":"$(whoami)" "$DEVENV_COURSIER_USER_DIR"