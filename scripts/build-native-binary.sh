#!/usr/bin/env bash
set -e

# Function to detect architecture
detect_architecture() {
  local os=$(uname -s | tr '[:upper:]' '[:lower:]')
  local arch=$(uname -m)

  case "$os" in
    darwin)
      case "$arch" in
        arm64|aarch64)
          echo "macos-arm64"
          ;;
        x86_64|amd64)
          echo "macos-amd64"
          ;;
        *)
          echo "macos-unknown"
          ;;
      esac
      ;;
    linux)
      case "$arch" in
        arm64|aarch64)
          echo "linux-arm64"
          ;;
        x86_64|amd64)
          echo "linux-amd64"
          ;;
        *)
          echo "linux-unknown"
          ;;
      esac
      ;;
    *)
      echo "unknown"
      ;;
  esac
}

# Function to detect current git branch
detect_branch() {
  if git rev-parse --git-dir > /dev/null 2>&1; then
    git branch --show-current 2>/dev/null || echo "local"
  else
    echo "local"
  fi
}

if [ $# -gt 3 ]; then
  echo "Usage: $0 [release] [architecture] [branch]"
  echo "Example: $0"
  echo "Example: $0 20260220-120000"
  echo "Example: $0 20260220-120000 macos-arm64"
  echo "Example: $0 20260220-120000 macos-arm64 main"
  echo ""
  echo "If release is not provided, it defaults to the current timestamp."
  echo "If architecture is not provided, it will be auto-detected."
  echo "If branch is not provided, it will be auto-detected from git (or default to 'local')."
  echo "Detected architecture: $(detect_architecture)"
  echo "Detected branch: $(detect_branch)"
  exit 1
fi

RELEASE=${1:-$(date +%Y%m%d-%H%M%S)}
ARCHITECTURE=${2:-$(detect_architecture)}
BRANCH=${3:-$(detect_branch)}

# Append "-dev" to version if branch is not "main" (if it doesn't already end with "-dev")
if [ "$BRANCH" != "main" ]; then
  if [[ ! "$RELEASE" =~ -dev$ ]]; then
    RELEASE="${RELEASE}-dev"
  fi
fi

export DEVENV_RELEASE="$RELEASE"
export DEVENV_ARCHITECTURE="$ARCHITECTURE"
export DEVENV_BRANCH="$BRANCH"

# Display build configuration with highlighting
BOLD='\033[1m'
RESET='\033[0m'

echo ""
echo "======================================"
echo "       BUILD CONFIGURATION"
echo "======================================"
echo ""
echo -e "  Version:      ${BOLD}${DEVENV_RELEASE}${RESET}"
echo -e "  Architecture: ${BOLD}${DEVENV_ARCHITECTURE}${RESET}"
echo -e "  Branch:       ${BOLD}${DEVENV_BRANCH}${RESET}"
echo ""
if [ "$DEVENV_BRANCH" != "main" ]; then
  echo "  ⚠️  Non-main branch detected!"
  echo "  ⚠️  '-dev' appended to version"
  echo ""
fi
echo "======================================"
echo ""

read -p "Continue with this configuration? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Build cancelled."
  exit 1
fi

sbt "cli/GraalVMNativeImage/packageBin"

BINARY_NAME="devenv-$RELEASE-$ARCHITECTURE"
echo "Binary name in GitHub release: $BINARY_NAME"


