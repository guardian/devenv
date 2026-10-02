# devenv rollout

This directory contains an automated process that updates repositories in the `guardian` organisation to a new devenv release. GitHub Copilot CLI does the work, and a GitHub Project board tracks the progress of each repository.

`rollout.sh` is a harness bash script that wraps GitHub Copilot CLI. It checks prerequisites, prepares a working directory and starts a Copilot CLI supervisor session in autopilot mode, using the prompt in `supervisor.prompt.md`. The supervisor manages the project board and delegates the work on each repository to the custom agents in `.github/agents`:

| Agent                | Model             | Purpose                                                                                              |
|----------------------|-------------------|------------------------------------------------------------------------------------------------------|
| `devenv-discovery`   | Claude Sonnet 5.5 | Finds repositories that pin a devenv version older than the target.                                  |
| `devenv-repo-change` | Claude Opus 5.5   | Updates the pin, applies any migrations, runs `devenv generate` and `devenv check`, and raises a PR. |
| `devenv-pr-check`    | GPT-6 Sol         | Independently checks that each PR contains the expected change and nothing else.                     |

The supervisor itself runs on Claude Opus 5.5.

## Prerequisites

- Copilot CLI, installed and logged in.
- The `gh` CLI, logged in with the `repo` and `project` scopes. You can add the project scope with `gh auth refresh -s project`.
- `git` and `mise`.
- Permission to create projects in the `guardian` organisation, and to push branches and open PRs in the repositories being updated.

## Usage

```bash
rollout/rollout.sh --version 20260721-123412 [--rediscover] [--repos agentsy,amigo] [--concurrency 4] [--dry-run]
```

`scripts/rollout-release.sh` runs the same script and takes the same options.

- `--version`: the target release tag. It must be a published production release, not a draft or a prerelease.
- `--rediscover`: re-run discovery on an existing board, so that repositories that have come into scope since the last run are added.
- `--repos`: a comma-separated allowlist of repositories. Only these repositories are considered.
- `--concurrency`: the maximum number of subagents running at once. The default is 4.
- `--dry-run`: use a separate board titled `devenv rollout <version> (dry run)`, and make no pushes, PRs, PR comments or merges. The harness blocks those commands through Copilot CLI's permission rules, and each item's card records the change that would have been made.
- `--max-continues`: the autopilot continuation limit for the supervisor. The default is 200.
- `--print-prompt`: print the rendered supervisor prompt and exit without contacting GitHub or starting Copilot.

The session transcript is saved to `rollout/work/logs/`.

## How it works

The board for a release is titled `devenv rollout <version>`. GitHub Projects are always owned by an organisation or a user, so the board belongs to `guardian`, and it is linked to `guardian/devenv` so that it appears on this repository's Projects tab. The supervisor only looks for existing boards among the projects linked to `guardian/devenv`. The board has one draft item for each in-scope repository, and the item moves through these columns:

- **Discovered**: the repository pins an older devenv version. Repositories on the target or a newer version are never added. `guardian/devenv` is never added either, because it deliberately does not pin a release and generates its own configuration from its own source.
- **In progress**: a subagent is updating the repository.
- **PR raised**: the PR is open and waiting for the owning team.
- **PR reviewed**: someone has reviewed or commented on the PR, or the automated PR check found a problem, so a person needs to look at it.
- **PR approved**: the PR is approved and will be merged once it merges cleanly and all of its checks pass.
- **PR merged (complete)**: the repository is on the new release.
- **Error**: something went wrong, and the item body describes the problem. Problems with the process itself appear as `Process error: ...` items.

When the board already exists, a run carries on from where the last one stopped. Approvals can take days, so run the script again later with the same version to move approved PRs through to merge. The supervisor never merges a PR that is not approved, has conflicts, or has failing or pending checks, and it never uses admin overrides.

A new board is created with a table view, because the CLI cannot change a project's layout. Switch its view to a board grouped by `Status` once, in the GitHub UI.

Before starting Copilot, the harness installs the target devenv release with mise and puts it first on `PATH`, so the agents always generate configuration with the target version. It also fetches the writing style instructions from `guardian/agentsy` into `rollout/work/.github/instructions`, where Copilot loads them for every agent. All commit messages, PR descriptions and comments follow that guide.

The change agent will not raise a new PR for a repository that already has a closed or merged PR from the branch `devenv/update-<version>`, because a closed PR usually means the owning team decided against the change. To retest the process on a repository, close the test PR, delete its branch and the board, and put `TEST ` at the start of the closed PR's title. Closed PRs with that prefix are ignored.

Upgrade guidance comes from the bodies of the GitHub releases between each repository's current version and the target. Any migration steps a release needs should be described in its release notes.
