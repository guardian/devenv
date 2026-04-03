#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if [[ -z $DEVENV_MISE_CACHE_MOUNT_DIR ]]; then
    warn "DEVENV_MISE_CACHE_MOUNT_DIR not set"
    exit 1
fi

if [[ -z $MISE_INSTALL_PATH ]]; then
    warn "MISE_INSTALL_PATH not set"
    exit 1
fi

# We will use this to chown below
DEVENV_CONTAINER_USER=$(whoami)

if test -f "$MISE_INSTALL_PATH" && $MISE_INSTALL_PATH --version; then
  log "mise is already present at $MISE_INSTALL_PATH."
else
  log "Installing mise..."
  curl -fsSL https://mise.run/bash | sh
fi

#log "Symlinking $MISE_INSTALL_PATH to /usr/local/bin/mise."
#sudo ln -sf "$MISE_INSTALL_PATH" /usr/local/bin/mise

log "Checking mise is working correctly."
$MISE_INSTALL_PATH --version

log "Updating mise if needed."
$MISE_INSTALL_PATH self-update --yes || warn "Skipping mise self-update - you may be offline."

log "Trusting mise config (see: https://mise.jdx.dev/cli/trust.html)."
$MISE_INSTALL_PATH trust --yes || true

log "Installing mise tooling."
$MISE_INSTALL_PATH install || warn "mise install failed. You may need to run mise install manually inside the container."

#echo -en "export PATH=/home/$DEVENV_CONTAINER_USER/.local/share/mise/shims:$PATH" | sudo tee -a /etc/bash.bashrc
#. /etc/bash.bashrc
eval "$(/mnt/mise-cache/mise activate --shims bash)"
#
#log "Reshimming mise shims."
#$MISE_INSTALL_PATH reshim

log "List installed tools."
$MISE_INSTALL_PATH list

log "Final checks."
$MISE_INSTALL_PATH doctor
