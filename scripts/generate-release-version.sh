#!/usr/bin/env bash
# Generate the version used by the Release workflow. Keeping this separate
# lets you preview or test version selection without building or publishing.
# Prints only the version to stdout and exits non-zero on failure.
#
# Usage (from the repository root; no arguments):
#   ./scripts/generate-release-version.sh                   # YYYYMMDD-HHMMSS
#   REF_NAME=feature ./scripts/generate-release-version.sh  # YYYYMMDD-HHMMSS-dev
#   HOTFIX=true ./scripts/generate-release-version.sh        # YYYYMMDD-HHMMSS.N
#
# Environment (optional unless needed for authentication):
#   HOTFIX             Defaults to false. Set to true to use the latest published
#                      production timestamp and increment its hotfix suffix,
#                      starting at .1. Draft hotfixes reserve their suffix.
#   REF_NAME           Ref being built (defaults to main).
#   REF_TYPE           Ref type (defaults to branch). A hotfix requires the
#                      main branch, not a tag or a different branch.
#   GH_TOKEN           Optional if gh is already authenticated. Hotfixes require
#                      gh with push access (to see draft releases) and jq.

set -euo pipefail

REF_NAME="${REF_NAME:-main}"
REF_TYPE="${REF_TYPE:-branch}"

if [[ "${HOTFIX:-false}" == true ]]; then
  if [[ "$REF_TYPE" != branch || "$REF_NAME" != main ]]; then
    echo 'Hotfix releases must be built from the main branch.' >&2
    exit 1
  fi

  # Drafts reserve their suffix but do not establish a new release base.
  gh api --paginate --slurp 'repos/guardian/devenv/releases?per_page=100' | jq -r '
    [ .[][] |
      (.tag_name | capture("^(?<base>[0-9]{8}-[0-9]{6})(?:\\.(?<suffix>[1-9][0-9]*))?$")) as $version |
      $version + {published: (.draft == false and .prerelease == false)}
    ] as $versions |
    ($versions | map(select(.published).base) | max) as $base |
    if $base == null then
      error("No published production release found to hotfix")
    else
      ($versions | map(select(.base == $base and .suffix != null).suffix | tonumber) | (max // 0) + 1) as $next |
      "\($base).\($next)"
    end
  '
else
  VERSION="$(date +%Y%m%d-%H%M%S)"
  if [[ "$REF_NAME" != main ]]; then
    VERSION="${VERSION}-dev"
  fi
  printf '%s\n' "$VERSION"
fi
