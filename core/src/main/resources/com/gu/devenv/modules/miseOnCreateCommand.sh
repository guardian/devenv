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

# We will use this to chown below
DEVENV_CONTAINER_USER=$(whoami)

DEVENV_CONTAINER_USER_MISE="/home/$DEVENV_CONTAINER_USER/.local/share/mise"
mkdir -p "$DEVENV_CONTAINER_USER_MISE"

log "Symlinking $DEVENV_MISE_CACHE_MOUNT_DIR/installs to $DEVENV_CONTAINER_USER_MISE/installs"
sudo mkdir -p "$DEVENV_MISE_CACHE_MOUNT_DIR/installs"
sudo ln -sf "$DEVENV_MISE_CACHE_MOUNT_DIR/installs" "$DEVENV_CONTAINER_USER_MISE/installs"

log "Symlinking $DEVENV_MISE_CACHE_MOUNT_DIR/downloads to $DEVENV_CONTAINER_USER_MISE/downloads"
sudo mkdir -p "$DEVENV_MISE_CACHE_MOUNT_DIR/downloads"
sudo ln -sf "$DEVENV_MISE_CACHE_MOUNT_DIR/downloads" "$DEVENV_CONTAINER_USER_MISE/downloads"

log "Ensuring correct ownership of the shared mise data volume."
sudo chown -RL "$DEVENV_CONTAINER_USER":"$DEVENV_CONTAINER_USER" "$DEVENV_MISE_CACHE_MOUNT_DIR"
sudo chown -RL "$DEVENV_CONTAINER_USER":"$DEVENV_CONTAINER_USER" "$DEVENV_CONTAINER_USER_MISE"