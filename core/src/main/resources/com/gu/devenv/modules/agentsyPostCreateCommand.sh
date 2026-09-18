#!/usr/bin/env bash

set -euo pipefail

readonly AGENTSY_REPOSITORY="https://github.com/guardian/agentsy.git"
readonly AGENTSY_BRANCH="main"
readonly AGENTSY_DIR="$HOME/agentsy"
readonly AGENTSY_BIN_DIR="$HOME/.local/bin"
readonly AGENTSY_LINK="$AGENTSY_BIN_DIR/agentsy"
readonly AGENTSY_ENTRYPOINT="$AGENTSY_DIR/agentsy"

ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }

# Update the existing Agentsy checkout when rebuilding the container. Clone it if it does not exist.
if [[ -e "$AGENTSY_DIR" || -L "$AGENTSY_DIR" ]]; then
  origin=$(git -C "$AGENTSY_DIR" remote get-url origin 2>/dev/null || true)
  if [[ "$origin" == "$AGENTSY_REPOSITORY" ]]; then
    log "Updating existing Agentsy checkout."
    git -C "$AGENTSY_DIR" pull --ff-only origin "$AGENTSY_BRANCH" ||
      warn "Unable to update Agentsy. Continuing with the existing checkout."
  else
    warn "$AGENTSY_DIR does not have the expected Git remote. Leaving it unchanged."
    exit 0
  fi
else
  log "Cloning Agentsy."
  git clone \
    --branch "$AGENTSY_BRANCH" \
    --depth 1 \
    "$AGENTSY_REPOSITORY" \
    "$AGENTSY_DIR"
fi

mkdir -p "$AGENTSY_BIN_DIR"
log "Adding Agentsy to $AGENTSY_BIN_DIR."
ln -sfn "$AGENTSY_ENTRYPOINT" "$AGENTSY_LINK"

"$AGENTSY_LINK" list >/dev/null
ok "Agentsy is available at $AGENTSY_LINK."
