#!/usr/bin/env bash
# Sets up mise inside the devcontainer after creation.
#
# Also logs useful info (including the output from `mise doctor`) to the terminal to help with debugging.

error() { printf "\033[1;31m[...] %s\033[0m\n" "$*"; }  # red
ok()    { printf "\033[1;32m[...] %s\033[0m\n" "$*"; }  # green
warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

if sudo dpkg -l | grep -w mise | grep '^ii'; then
  log "Updating mise..."

  sudo apt-get update -o Dir::Etc::sourcelist=/etc/apt/sources.list.d/mise.list
  sudo apt-get install --only-upgrade mise
else
  log "Installing mise..."

  sudo install -db 755 /etc/apt/keyrings
  curl -Ls https://mise.jdx.dev/gpg-key.pub | sudo tee /etc/apt/keyrings/mise-archive-keyring.asc

  echo "deb [signed-by=/etc/apt/keyrings/mise-archive-keyring.asc] https://mise.jdx.dev/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/mise.list

  sudo apt-get update -o Dir::Etc::sourcelist=/etc/apt/sources.list.d/mise.list
  sudo apt-get install -y mise

fi

log "Checking mise is working correctly and on the path."
mise --version

log "Trusting mise config (see: https://mise.jdx.dev/cli/trust.html)."
mise trust --yes || true

log "Installing mise tooling."
mise install || warn "mise install failed. You may need to run mise install manually inside the container."

log "Ensure that mise activate --shims bash is in bashrc."
sed -i 's/mise activate bash/mise activate --shims bash/' ~/.bashrc
grep 'mise activate --shims bash' ~/.bashrc || echo 'eval "$(mise activate --shims bash)" #Added by devenv' | tee ~/.bashrc

log "Check there is exactly one activate command in bashrc."
test "$(grep -c 'mise activate' ~/.bashrc)" -eq 1 || echo 'Did not find exactly one activate command'

log "Run the activate command from bashrc."
eval "$(grep 'mise activate' ~/.bashrc)"

log "List installed tools."
mise list

log "Final checks."
mise doctor
