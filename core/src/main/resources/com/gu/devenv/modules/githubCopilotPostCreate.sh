#!/usr/bin/env bash

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

# Adds `gh` (GitHub CLI) and `copilot` (GitHub Copilot CLI) to the user's PATH via mise.
if command -v mise >/dev/null 2>&1; then
  log "Installing GitHub CLI"
  mise use gh@latest --global

  log "Installing GitHub Copilot CLI"
  mise use copilot@latest --global
else
  error "mise is required; skipping tool installation and checks."
  exit 1
fi
