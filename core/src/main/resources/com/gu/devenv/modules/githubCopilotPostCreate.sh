#!/usr/bin/env bash

set -euo pipefail

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

install_agentsy() {
  local -r repository="https://github.com/guardian/agentsy.git"
  local -r branch="main"
  local -r agentsy_dir="$HOME/agentsy"
  local -r entrypoint="$agentsy_dir/agentsy"
  local origin

  if [[ -e "$agentsy_dir" || -L "$agentsy_dir" ]]; then
    origin=$(git -C "$agentsy_dir" remote get-url origin 2>/dev/null || true)
    if [[ "$origin" == "$repository" ]]; then
      log "Updating Agentsy."
      mise exec -- "$entrypoint" update ||
        warn "Unable to update Agentsy. Continuing with the existing checkout."
      return 0
    else
      warn "$agentsy_dir does not have the expected Git remote. Leaving it unchanged."
      return 0
    fi
  else
    log "Cloning Agentsy."
    git clone --branch "$branch" --depth 1 "$repository" "$agentsy_dir" || return
  fi

  log "Installing Agentsy."
  # Need to prefix with `mise exec ---` so that we can use copilot inside the following command
  mise exec -- "$entrypoint" install || return
  ok "Agentsy installed."
}

# Adds `gh` (GitHub CLI) and `copilot` (GitHub Copilot CLI) to the user's PATH via mise.
if command -v mise >/dev/null 2>&1; then
  log "Installing GitHub CLI"
  mise use gh@latest --global

  log "Installing GitHub Copilot CLI"
  mise use copilot@latest --global

  install_agentsy || {
    error "Failed to install Agentsy."
    exit 1
  }

else
  error "mise is required; skipping tool installation and checks."
  exit 1
fi
