#!/usr/bin/env bash

warn()  { printf "\033[1;33m[...] %s\033[0m\n" "$*"; }  # gold
log()   { printf "\033[1;36m[...] %s\033[0m\n" "$*"; }  # cyan

# Adds `gh` (GitHub CLI) and `copilot` (GitHub Copilot CLI) to the user's PATH via mise.
mkdir -p ~/.config/mise
cat > ~/.config/mise/config.toml <<'EOF'
gh      latest
copilot latest
EOF

if command -v mise >/dev/null 2>&1; then
  log "Running 'mise install' from home directory."
  (cd ~ && mise install)

  log "Checking GitHub CLI is working correctly and on the path."
  gh --version

  log "Checking GitHub Copilot CLI is working correctly and on the path."
  copilot --version
else
  warn "mise is not installed yet; skipping tool installation and checks."
fi
