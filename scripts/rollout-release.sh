#!/usr/bin/env bash
#
# Rolls out a devenv release to repositories across the guardian organisation.
# The process lives in rollout/, and rollout/README.md describes how it works.

set -euo pipefail

exec "$(dirname "${BASH_SOURCE[0]}")/../rollout/rollout.sh" "$@"
