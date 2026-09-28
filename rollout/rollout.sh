#!/usr/bin/env bash
#
# Runs the AI-orchestrated rollout of a devenv release across the guardian
# organisation. This script checks prerequisites, prepares a working
# directory and hands over to a Copilot CLI supervisor session. See README.md
# in this directory for details.

set -euo pipefail

ROLLOUT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK_DIR="$ROLLOUT_DIR/work"
ORG="guardian"
SUPERVISOR_MODEL="claude-opus-5.5"
STYLE_GUIDE_REPO="guardian/agentsy"
STYLE_GUIDE_PATH="instructions/writing-style.instructions.md"

VERSION=""
REDISCOVER="false"
REPOS=""
CONCURRENCY="4"
DRY_RUN="false"
PRINT_PROMPT="false"
MAX_CONTINUES="200"

usage() {
  cat <<EOF
Usage: $(basename "$0") --version <release> [options]

Options:
  --version <release>    Target devenv release tag, e.g. 20260721-123412 (required)
  --rediscover           Re-run discovery even if the project board already exists
  --repos <a,b,c>        Only consider these repositories (names or $ORG/<name>)
  --concurrency <n>      Maximum number of subagents running at once (default: $CONCURRENCY)
  --dry-run              Use a separate dry-run board and make no pushes, PRs or merges
  --max-continues <n>    Autopilot continuation limit for the supervisor (default: $MAX_CONTINUES)
  --print-prompt         Print the rendered supervisor prompt and exit without running anything
  -h, --help             Show this help
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

require_value() {
  [[ $# -ge 2 && -n "$2" && "$2" != --* ]] || die "$1 requires a value"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) require_value "$@"; VERSION="$2"; shift 2 ;;
    --repos) require_value "$@"; REPOS="$2"; shift 2 ;;
    --concurrency) require_value "$@"; CONCURRENCY="$2"; shift 2 ;;
    --max-continues) require_value "$@"; MAX_CONTINUES="$2"; shift 2 ;;
    --rediscover) REDISCOVER="true"; shift ;;
    --dry-run) DRY_RUN="true"; shift ;;
    --print-prompt) PRINT_PROMPT="true"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "unknown argument: $1" ;;
  esac
done

[[ -n "$VERSION" ]] || { usage >&2; die "--version is required"; }
[[ "$VERSION" =~ ^[0-9]{8}-[0-9]{6}$ ]] \
  || die "--version must be a production release tag of the form YYYYMMDD-HHMMSS, got '$VERSION'"
[[ "$CONCURRENCY" =~ ^[1-9][0-9]*$ ]] || die "--concurrency must be a positive integer"
[[ "$MAX_CONTINUES" =~ ^[1-9][0-9]*$ ]] || die "--max-continues must be a positive integer"

# Normalise the allowlist to a comma-separated list of owner/name values.
REPO_LIST=""
if [[ -n "$REPOS" ]]; then
  IFS=',' read -ra repo_items <<< "$REPOS"
  for item in "${repo_items[@]}"; do
    item="${item//[[:space:]]/}"
    [[ -n "$item" ]] || continue
    [[ "$item" == */* ]] || item="$ORG/$item"
    [[ "$item" =~ ^$ORG/[A-Za-z0-9._-]+$ ]] || die "invalid repository in --repos: '$item'"
    [[ "$item" != "$ORG/devenv" ]] \
      || die "$ORG/devenv generates its own configuration and is never pinned, so it cannot be rolled out to"
    REPO_LIST="${REPO_LIST:+$REPO_LIST,}$item"
  done
  [[ -n "$REPO_LIST" ]] || die "--repos was given but contains no repositories"
fi

BOARD_TITLE="devenv rollout $VERSION"
[[ "$DRY_RUN" == "true" ]] && BOARD_TITLE="$BOARD_TITLE (dry run)"

render_prompt() {
  local prompt
  prompt="$(<"$ROLLOUT_DIR/supervisor.prompt.md")"
  prompt="${prompt//\{\{VERSION\}\}/$VERSION}"
  prompt="${prompt//\{\{ORG\}\}/$ORG}"
  prompt="${prompt//\{\{BOARD_TITLE\}\}/$BOARD_TITLE}"
  prompt="${prompt//\{\{REDISCOVER\}\}/$REDISCOVER}"
  prompt="${prompt//\{\{REPOS\}\}/${REPO_LIST:-none}}"
  prompt="${prompt//\{\{CONCURRENCY\}\}/$CONCURRENCY}"
  prompt="${prompt//\{\{DRY_RUN\}\}/$DRY_RUN}"
  prompt="${prompt//\{\{WORK_DIR\}\}/$WORK_DIR}"
  printf '%s\n' "$prompt"
}

if [[ "$PRINT_PROMPT" == "true" ]]; then
  render_prompt
  exit 0
fi

# Preflight checks.
for tool in copilot gh git mise; do
  command -v "$tool" >/dev/null 2>&1 || die "'$tool' was not found on PATH"
done

auth_status="$(gh auth status 2>&1)" || die "gh is not logged in: run 'gh auth login'"
for scope in repo project; do
  grep -q "'$scope'" <<< "$auth_status" \
    || die "the gh token is missing the '$scope' scope: run 'gh auth refresh -s $scope'"
done

release_state="$(gh release view "$VERSION" --repo "$ORG/devenv" --json isDraft,isPrerelease \
  --jq '"\(.isDraft) \(.isPrerelease)"' 2>/dev/null)" \
  || die "release '$VERSION' was not found in $ORG/devenv"
[[ "$release_state" == "false false" ]] \
  || die "release '$VERSION' is a draft or a prerelease, and cannot be rolled out"

# Instructions under .github/instructions in the session's working directory
# are loaded by Copilot for the supervisor and its subagents.
mkdir -p "$WORK_DIR/repos" "$WORK_DIR/logs" "$WORK_DIR/bin" "$WORK_DIR/.github/instructions"
style_guide="$(gh api "repos/$STYLE_GUIDE_REPO/contents/$STYLE_GUIDE_PATH" --jq .content)" \
  || die "could not fetch the writing style guide from $STYLE_GUIDE_REPO"
base64 -d <<< "$style_guide" > "$WORK_DIR/.github/instructions/writing-style.instructions.md"

# Install the target devenv release and put it first on PATH, so the agents use
# it regardless of the version pinned in the repository they are working in.
mise install "github:$ORG/devenv@$VERSION" >/dev/null \
  || die "could not install devenv $VERSION with mise"
devenv_dir="$(mise where "github:$ORG/devenv@$VERSION")"
devenv_bin="$(find "$devenv_dir" -type f -name 'devenv*' -perm -u+x | head -n 1)"
[[ -n "$devenv_bin" ]] || die "could not find the devenv binary in $devenv_dir"
ln -sf "$devenv_bin" "$WORK_DIR/bin/devenv"
export PATH="$WORK_DIR/bin:$PATH"
devenv version | grep -q "$VERSION" \
  || die "the devenv binary in $devenv_dir does not report version $VERSION"

copilot_args=(
  -C "$WORK_DIR"
  -p "$(render_prompt)"
  --autopilot
  --max-autopilot-continues "$MAX_CONTINUES"
  --model "$SUPERVISOR_MODEL"
  --add-dir "$ROLLOUT_DIR"
  --allow-all-tools
  --disable-builtin-mcps
  --deny-tool 'shell(gh repo delete)'
  --deny-tool 'shell(gh project delete)'
  --share "$WORK_DIR/logs/session-$(date +%Y%m%d-%H%M%S).md"
)

# In a dry run, commands that would change another repository are blocked by
# the CLI's permission rules as well as by the prompt.
if [[ "$DRY_RUN" == "true" ]]; then
  copilot_args+=(
    --deny-tool 'shell(git push)'
    --deny-tool 'shell(gh pr create)'
    --deny-tool 'shell(gh pr merge)'
    --deny-tool 'shell(gh pr comment)'
    --deny-tool 'shell(gh pr review)'
    --deny-tool 'shell(gh pr edit)'
  )
fi

echo "Starting supervisor for '$BOARD_TITLE' in $WORK_DIR"
exec copilot "${copilot_args[@]}"
