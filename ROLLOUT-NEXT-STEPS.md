# Rollout testing: next steps

These notes are for picking up the rollout work in a fresh session. The work is on the `automate-release-process` branch, in draft PR #115 (https://github.com/guardian/devenv/pull/115). This file is not meant to be committed.

## Where things stand

- The harness is `rollout/rollout.sh`, and it can also be run as `scripts/rollout-release.sh`. `rollout/README.md` describes how the process works.
- The target release for testing is `20260930-111347`, which is the latest production release. agentsy is on `20260721-123412`, so it is in scope.
- Dry run 1 (agentsy only) worked, apart from failing to create a custom `Repository` field, because that name is reserved in Projects. The field has been removed from the prompt, but that fix has not been tested yet. The dry-run board from that run (project 161) has already been deleted.
- The supervisor now treats a PR as approved only when a person has submitted an approving review and no reviewer has an outstanding request for changes.
- Nothing has been tested yet on real PRs, merging, the PR check agent, resuming from an existing board, `--rediscover`, `mise.toml` pins, repositories that need `runArgs` migrations, or discovery across the whole org.

## Before each run

1. Make sure `automate-release-process` is checked out and up to date, because the harness runs the prompts and agents from the working tree.
2. Check that the `gh` token has the `repo` and `project` scopes with `gh auth status`. If it doesn't, run `gh auth refresh -s project`.
3. Optionally preview the prompt without side effects: `scripts/rollout-release.sh --version 20260930-111347 --repos agentsy --dry-run --print-prompt`.
4. While it runs, the full transcript is written to `rollout/work/logs/session-<timestamp>.md`. The supervisor's final summary only appears in that file, not in the terminal.

## Cleaning up after a run

Each run leaves some or all of the following behind:

- **The project board.** Find it with `gh project list --owner guardian | grep "devenv rollout"` and delete it with `gh project delete <number> --owner guardian`. Delete it rather than closing it, because a closed board with the same title makes the supervisor stop with a process error.
- **The PR and branch** (real runs only). Close the PR with `gh pr close <url> --delete-branch`. If a branch named `devenv/update-<version>` is still left over, delete it with `git push origin --delete devenv/update-<version>` from a clone, or through the GitHub UI. The change agent stops with a failure if it finds a leftover branch or a closed PR for the same version, so both have to be removed before a retest.
- **Local state.** Remove the clones with `rm -rf rollout/work/repos`. Keep `rollout/work/logs` for reference.

A merged PR cannot be undone this way. After step 3, agentsy will be on the target release, so it will not be in scope for later runs against the same version. This is expected.

## Steps

### 1. Dry run on agentsy, to check the board fix

```bash
scripts/rollout-release.sh --version 20260930-111347 --repos agentsy --dry-run
```

Check:

- A board titled `devenv rollout 20260930-111347 (dry run)` is created, linked to `guardian/devenv`, with the seven Status columns and the `Current version` and `PR` fields.
- There is no `Process error` card.
- The agentsy card ends in `In progress` with a dry-run report describing the diff (the `.tool-versions` pin and the regenerated `.devcontainer/shared/devcontainer.json`).
- agentsy has no new branch or PR.

Then clean up by deleting the board and `rollout/work/repos`.

### 2. Improve the end-of-run output (optional but useful)

Before the larger runs, it would help if the supervisor's final summary appeared in the terminal. One option is for the harness to print the end of the `--share` transcript after `copilot` exits. The mise "installed but not activated" warning could also be silenced. Neither change is required for the tests below.

### 3. Real run on agentsy, to check the PR process

Agentsy is maintained by us, so there's no need to communicate this change to another team.

```bash
scripts/rollout-release.sh --version 20260930-111347 --repos agentsy
```

Check after the first run:

- The PR has the title `Update devenv to 20260930-111347`, carries a required label if the repository enforces one, and its description follows the writing style guide. The description should cover what changed, what the team needs to do, and, below a divider, how the PR was created.
- The PR check agent ran and passed. If it failed, it should have commented on the PR, and the card should be in `PR reviewed`.
- The card is in `PR raised` and its `PR` field links to the PR.

Then test the remaining paths by running the same command again at each stage:

1. **Resume with nothing to do.** Run it again straight away. It should find the existing board, leave the card in `PR raised` and exit quickly.
2. **Review comments.** Have someone leave a comment on the PR without approving it, then rerun. The card should move to `PR reviewed`, with a note naming the reviewer. Nothing on the PR should change.
3. **Approval and merge.** Have someone approve the PR, then rerun. The card should move from `PR reviewed` to `PR approved`, and the PR should then be squash-merged once its checks pass, with the card ending in `PR merged (complete)`. If checks are still running, rerun later.

If there is time, also try stopping a run with Ctrl-C while the change agent is working. The next run should find the card in `In progress` and pick it up again rather than duplicating the work.

Clean up by deleting the board and `rollout/work/repos`. The merge itself stays.

### 4. Dry run across the org

This is the first run without an allowlist, so it tests discovery properly. It is still a dry run, so nothing is pushed, but every in-scope repository is cloned and changed locally, which takes a while and uses a lot of AI credits. Dry run 1 used about 68 credits for one repository.

```bash
scripts/rollout-release.sh --version 20260930-111347 --dry-run
```

Check:

- The discovered list looks complete. Compare it with a manual code search for `guardian/devenv` in `.tool-versions`, `mise.toml` and `.devcontainer/devenv.yaml`, and look for any repository that should have been found but wasn't.
- `guardian/devenv` is not on the board, and neither is any repository already on `20260930-111347` or a newer release.
- Repositories that have a devenv config but no pin are in `Error` with a clear explanation.
- The dry-run reports for repositories that pin in `mise.toml`, and for repositories with `runArgs` or an escape hatch, show the migrations from the release notes being applied correctly.
- The concurrency limit held, with no more than four agents running at once.

Also test `--rediscover` by rerunning with it on the same board. Nothing should be duplicated.

Clean up by deleting the board and `rollout/work/repos`.

### 5. Before the real rollout

- Consider merging PR #115 first, so the rollout runs from `main`.
- Let teams know the PRs are coming, what they need to do (approve the PR, then rebuild their devcontainers after it merges), and that DevX/AI and Automation owns the process.

### 6. Real org-wide rollout

```bash
scripts/rollout-release.sh --version 20260930-111347
```

Then rerun the same command every day or so. Each run moves approved PRs to merge and updates the board. Cards in `PR reviewed` and `Error` need a person to look at them, and the board gives an overview of where every repository stands.
