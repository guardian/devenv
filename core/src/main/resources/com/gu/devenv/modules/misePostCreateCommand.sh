#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if which mise && mise --version; then
  log "mise is already present at $(which mise)."
else
  log "Installing mise..."
  curl -fsSL https://mise.run/bash | sh
fi

log "Checking mise is working correctly and on the path."
mise --version

log "Updating mise if needed."
mise self-update --yes || warn "Skipping mise self-update - you may be offline."

# Set a minimum release age so mise only installs tool versions that have existed for a while.
# This guards against very freshly published versions that have not yet had time for issues to
# surface. We write it to a repo-local mise.toml (settings live alongside any .tool-versions file).
# See: https://mise.jdx.dev/configuration/settings.html#minimum_release_age
MISE_MINIMUM_RELEASE_AGE="7d"
log "Adding a ${MISE_MINIMUM_RELEASE_AGE} minimum release age to mise.toml..."
if [[ -f mise.toml ]]; then
  if grep -q 'minimum_release_age' mise.toml; then
    log "minimum_release_age already configured in mise.toml; leaving it unchanged."
  else
    warn "mise.toml already exists without minimum_release_age; not modifying it automatically."
  fi
else
  printf '[settings]\nminimum_release_age = "%s"\n' "$MISE_MINIMUM_RELEASE_AGE" > mise.toml
  ok "Wrote mise.toml with minimum_release_age = \"${MISE_MINIMUM_RELEASE_AGE}\"."
fi

log "Trusting mise config (see: https://mise.jdx.dev/cli/trust.html)."
mise trust --yes || true

log "Installing mise tooling."
mise install || warn "mise install failed. You may need to run mise install manually inside the container."

log "Ensure that mise activate --shims bash is in bashrc."
sed -i 's/mise activate bash/mise activate --shims bash/' ~/.bashrc
grep 'mise activate --shims bash' ~/.bashrc || echo 'eval "$(mise activate --shims bash)" #Added by devenv' | tee -a ~/.bashrc

log "Check there is exactly one activate command in bashrc."
test "$(grep -c 'mise activate' ~/.bashrc)" -eq 1 || echo 'Did not find exactly one activate command'

log "Run the activate command from bashrc."
eval "$(grep 'mise activate' ~/.bashrc)"

log "List installed tools."
mise list

log "Final checks."
mise doctor
