#!/usr/bin/env bash
# Sets up JVM-related caches inside the devcontainer after creation.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if [[ -z $IVY_DATA_DIR ]]; then
    warn "IVY_DATA_DIR not set"
    exit 1
fi

IVY_CACHE_DIR_ROOT="/home/$(whoami)/.ivy2"
IVY_CACHE_DIR_LINK="$IVY_CACHE_DIR_ROOT/cache"

log "Link the shared ivy data volume at the correct point."
mkdir -p "$IVY_CACHE_DIR_LINK"
rmdir "$IVY_CACHE_DIR_LINK"
ln -sf "$IVY_DATA_DIR" "$IVY_CACHE_DIR_LINK"

log "Ensuring correct ownership of the shared ivy data volume."
sudo chown -R "$(whoami)":"$(whoami)" "$IVY_CACHE_DIR_ROOT"

if [[ -z $COURSIER_DATA_DIR ]]; then
    warn "COURSIER_DATA_DIR not set"
    exit 1
fi

COURSIER_CACHE_DIR_ROOT="/home/$(whoami)/.cache"
COURSIER_CACHE_DIR_LINK="$COURSIER_CACHE_DIR_ROOT/coursier/v1"

log "Link the shared coursier data volume at the correct point."
mkdir -p "$COURSIER_CACHE_DIR_LINK"
rmdir "$COURSIER_CACHE_DIR_LINK"
ln -sf "$COURSIER_DATA_DIR" "$COURSIER_CACHE_DIR_LINK"

log "Ensuring correct ownership of the shared coursier data volume."
sudo chown -R "$(whoami)":"$(whoami)" "$COURSIER_CACHE_DIR_ROOT"