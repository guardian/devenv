# Namespacing and versioning Copilot plugins

Research date: 10 November 2026. Primary focus: GitHub Copilot CLI plugins, with VS Code compatibility and the current repository's integration as context.

## Executive Summary

Copilot plugins have several distinct identities: the plugin manifest name, marketplace-qualified identifiers such as `plugin@marketplace`, component names, and client-extension namespaces such as `com.github.copilot`; these are not one universal namespace.[^1][^2][^3] Marketplace qualification identifies a distribution source, but does not automatically prevent agent, command or MCP server collisions.[^4][^5] Plugin release `version` is optional metadata, while a catalog source's full Git commit `sha` is the documented mechanism for selecting immutable source contents; npm-style `plugin@version` resolution is not established by the reviewed references.[^6][^7][^8] The recommended approach is stable organisational names, explicit component IDs, release labels kept consistent by policy, and promotion of tested commit pins rather than reliance on version strings alone.[^4][^6][^7] In this checkout, the practical reproducibility issue is upstream of plugin manifests: the bootstrap installs `copilot@latest` and tracks `guardian/agentsy` on `main`.[^9]

## 1. Scope and terminology

This is primarily a conceptual and practical how-to report, not a verified implementation deep-dive. Six focused research investigations covered local integration, CLI identity, version/update semantics, cross-client compatibility, reproducible distribution, and unresolved collision cases. Findings below distinguish documented behaviour, deductions, and recommendations.

| Term | Meaning | What it does not establish |
|---|---|---|
| Plugin `name` | Package identity declared in `plugin.json`.[^2] | Universal isolation of everything in the package.[^4] |
| Marketplace `name` | Catalog registration key, taken from its manifest.[^1] | A repository name or freely selectable local alias.[^1] |
| `plugin@marketplace` | Marketplace-qualified installation/settings identifier.[^1][^8] | A semantic-version selector.[^8] |
| Component identity | Agent filename ID, skill frontmatter name, command filename, or MCP server key.[^4][^5] | A uniform plugin-prefixed naming rule.[^4][^5] |
| `com.github.copilot` | Client-owned Agent Plugins extension namespace.[^3] | The publishing organisation's namespace or the plugin's own name.[^3] |
| `plugin.json.version` | Optional plugin release metadata.[^6] | A documented dependency-resolution or upgrade-ordering contract.[^6][^10] |
| `$schema` | Manifest format/validation contract.[^2] | The release version of the plugin.[^6] |
| Git `ref` / `sha` | Source revision selectors; full `sha` pins a commit.[^7] | A complete freeze of external executables and services.[^11] |

### Repository context

| Repository | Observed role | Evidence boundary |
|---|---|---|
| [guardian/devenv](https://github.com/guardian/devenv) | Configures IDE plugins and bootstraps Copilot CLI and Agentsy.[^9][^12] | Read locally at clean commit `59b8287c82271beeaf36336baaab1bc1bd2e01b2`. |
| [guardian/agentsy](https://github.com/guardian/agentsy) | External repository cloned or updated before `agentsy install` runs.[^9] | Its implementation was not available in the investigated checkout; downstream plugin contents remain unverified. |

The local `Plugins` model contains IntelliJ and VS Code lists. The Copilot module supplies `com.github.copilot` and `GitHub.copilot` respectively, and generation places them under the corresponding devcontainer IDE customisations.[^12] These IDE identifiers must not be treated as evidence of a local Copilot CLI marketplace or Agent Plugins package.

The local inventory found no `plugin.json` or `marketplace.json` in the searched repository files, excluding Git/build/dependency directories. This is a bounded negative search result, not a claim about external repositories or user configuration. The concrete integration boundary is the call to `agentsy install`.[^9]

## 2. Namespacing: identity is not isolation

### Plugin and marketplace names

For **Agent Plugins**, names are 1–64 characters, use lowercase ASCII letters, digits, hyphens and periods, begin/end with an alphanumeric character, and cannot contain consecutive hyphens or consecutive periods. The **legacy CLI format** instead documents kebab-case names using letters, numbers and hyphens, with a maximum of 64 characters.[^2]

A marketplace's manifest `name` becomes its registration key; the CLI does not provide an arbitrary local alias when registering it. Renaming a marketplace changes plugin identifiers used in settings.[^1]

For example, the documented shape:

```text
guardian-platform-tools@guardian-platform-stable
```

would identify the illustrative plugin `guardian-platform-tools` in the illustrative marketplace `guardian-platform-stable`. The suffix is a marketplace, not a release version.[^8]

**Recommendation:** use durable organisation/team prefixes, such as `guardian-platform-tools`, and keep marketplace entry names equal to plugin manifest names. This is an organisational convention, not an automatic namespace feature; mismatch behaviour is not specified in the reviewed references.[^1][^2][^13]

### Component collision matrix

| Component | Documented collision handling | Authoring consequence |
|---|---|---|
| Agents | First-found wins by filename-derived ID; `reviewer.agent.md` becomes `reviewer`. Personal/project definitions can shadow plugin agents.[^4] | Give files distinctive IDs; changing only a display name does not solve an ID collision. |
| Skills | Command reference explicitly allows plugin-qualified invocation, such as `/my-plugin/search` and `/other-plugin/search`; the bare name selects the higher-priority plugin.[^5] | Qualification can help for differently named plugins, subject to the documentation caveat below. |
| Commands | Explicitly excluded from skill coexistence. Higher-priority sources win; commands also lose to same-name skills.[^5] | Do not assume every command gains a usable `/plugin:command` alias. |
| MCP servers | Between plugins using the same server key, the last-loaded plugin wins, with a warning. Additional command-line MCP configuration has higher priority.[^4][^14] | Use distinctive server keys; changing the plugin name alone does not isolate them. |
| Hooks | Matching events combine: all hook entries from the contributing sources run.[^15] | Design for coexistence, not replacement by a same-named event. |

**Documentation caveat:** the plugin reference broadly describes skills as first-found-wins by name, while the command reference specifically describes plugin-qualified coexistence. The more specific passage supports `/plugin/skill` for its stated case, but does not settle all shadowing scenarios.[^4][^5]

A colon may appear in an authored skill name, such as `my-plugin:search`; that is separate from the documented slash-based `/my-plugin/search` invocation. Neither establishes a universal automatic prefix for command files.[^5]

### Unresolved naming cases

The reviewed references do not explicitly define:

- How `/plugin/skill` distinguishes two identically named plugins from different marketplaces. Management identifiers include the marketplace, but the documented skill syntax does not.[^1][^5]
- What happens if a catalog entry's name differs from the fetched plugin manifest's name.[^2][^13]
- Whether qualified access always survives a project/personal skill shadowing a plugin skill.[^4][^5]
- Automatic aliases, settings rewrites or persistent-data migration following a rename.[^1][^16]

**Recommendation:** do not rely on those behaviours. Avoid co-installing identically named staging/stable variants in the same test environment, and treat renaming as a migration requiring explicit settings and data planning.

### Storage and security boundaries

The CLI documents marketplace installs under `~/.copilot/installed-plugins/MARKETPLACE/PLUGIN-NAME` and direct installs under `~/.copilot/installed-plugins/_direct/SOURCE-ID/`. Persistent plugin data is organised by marketplace/plugin identity, with `${PLUGIN_DATA}` identifying writable storage unique to the installed plugin.[^16]

Those paths and names are **not a security sandbox**. Hook sandboxing is documented separately and applies when the session sandbox is enabled; namespace selection itself does not provide that execution policy.[^17]

## 3. Versioning: distinguish labels, schemas and source revisions

### What each version means

| Field or value | Documented meaning | Important limit |
|---|---|---|
| `plugin.json.version` | Optional plugin-release string.[^6] | Agent Plugins recommends SemVer but does not reject a string merely for being non-SemVer. |
| Catalog `plugins[].version` | Optional plugin version in the catalog.[^13] | Precedence against the manifest version is not established. |
| Catalog `metadata.version` | Optional marketplace metadata.[^13] | No installed-plugin upgrade-control semantics were established. |
| `$schema` containing `1.0.0` or `1.1.0` | Agent Plugins format version recognised by current CLI documentation.[^2] | Not the plugin's release version. Unsupported Agent Plugins schema versions are rejected. |
| Source `ref` | Git branch/tag/ref selector.[^7] | A branch, and potentially a tag, can move. |
| Source `sha` | Full 40-character commit identifier.[^7] | Pins repository contents, not every external runtime dependency. |
| Copilot CLI `1.0.107` | Executable/help version observed by researchers. | Separate from every plugin and manifest format version. |

The legacy manifest reference calls its optional `version` a semantic version, but the research did not establish its runtime validation algorithm. Do not extrapolate Agent Plugins' explicit permissiveness to every legacy implementation.[^6]

### Does a version bump control updates?

**Not established.** Official documentation says updates fetch/install the latest version and describes available update actions, but does not explain whether detection compares manifest versions, catalog metadata, Git revisions, or another signal.[^10]

Consequently, this report does **not** claim that:

- A version bump is required for an update.
- Same-version content changes are always detected.
- A lower version is rejected or accepted.
- Manifest `version` is purely cosmetic.
- Version ranges, a plugin dependency resolver, or a lockfile are supported.

The documented install forms establish marketplace and source selection, not an npm-style `plugin@1.2.3` release resolver.[^8] Use SemVer labels as a recommended release communication policy, and source pins for the separate reproducibility requirement.[^6][^7]

### Two layers of source selection

The CLI accepts marketplace registration as `OWNER/REPO#REF`. Separately, a catalog entry can select a plugin repository, optional subdirectory and full `sha`.[^7][^18]

```text
Marketplace repository/ref
        |
        v
Catalog entry: plugin name + source repository/path/SHA
        |
        v
Committed plugin contents
        |
        v
Components and separately resolved runtime dependencies
```

**Deduction:** moving the catalog ref can select a different entry and therefore a different plugin SHA. An unchanged entry pin continues to identify the same plugin commit. The documentation does not specify ancestry/mismatch checks when both `ref` and `sha` appear in one source object.[^7][^18]

A SHA does not freeze an MCP executable found on `PATH`, an `npx` package resolved at runtime, or a remote HTTP service. The official examples include Node execution, dynamically resolved packages, and remote endpoints; control these separately when reproducibility matters.[^11]

## 4. Installation and update operations

Documented command shapes include the following. Uppercase terms are placeholders.[^8][^10][^18]

```sh
# Register a marketplace, optionally selecting its Git ref
copilot plugin marketplace add OWNER/REPO#REF

# Install a marketplace plugin or a direct source
copilot plugin install PLUGIN@MARKETPLACE
copilot plugin install OWNER/REPO:PATH/TO/PLUGIN
copilot plugin install ./my-plugin

# Inspect and update installed plugins
copilot plugin list --json
copilot plugin update PLUGIN@MARKETPLACE
copilot plugin update --all

# Refresh the catalog separately
copilot plugin marketplace update MARKETPLACE

# Remove a marketplace plugin
copilot plugin uninstall PLUGIN@MARKETPLACE
```

Catalog refresh and installed-plugin update are distinct operations. The references do not establish that every plugin-update invocation first refreshes its catalog.[^10][^18]

### Automatic and local updates

- Built-in `awesome-copilot` plugins can update automatically at session start in a trusted working directory. Global `autoUpdate: false` or `COPILOT_AUTO_UPDATE=false` disables automatic updating; it is skipped by default in CI.[^10]
- Added marketplaces can opt in through `extraKnownMarketplaces` entries with `autoUpdate: true`. The documented opt-in applies to interactive and `-p` sessions, not SDK/server sessions; repository-level settings cannot authorise it.[^10]
- Direct local installs cache components: re-run `copilot plugin install ./my-plugin` to pick up changes.[^19]
- Local-directory marketplaces have a documented exception: path-sourced plugins load from their actual directories, with changes taking effect on restart/new session rather than requiring plugin update.[^10]

**Recommendation:** explicitly choose an automatic-update policy for reproducibility-sensitive environments. A release label by itself does not establish that installed contents remain fixed.[^6][^10]

## 5. A practical release convention

The following is a **recommended organisational design**, not built-in channel or dependency-management functionality. It uses the documented manifest, catalog and source-pin mechanisms.[^2][^7][^13]

### Illustrative manifests

Plugin repository root, `plugin.json`:

```json
{
  "$schema": "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
  "name": "guardian-platform-tools",
  "version": "1.2.0"
}
```

Marketplace repository, `.github/plugin/marketplace.json`:

```json
{
  "name": "guardian-platform-stable",
  "owner": {
    "name": "Guardian Platform"
  },
  "plugins": [
    {
      "name": "guardian-platform-tools",
      "version": "1.2.0",
      "source": {
        "source": "github",
        "repo": "guardian/example-platform-plugins",
        "sha": "0123456789012345678901234567890123456789"
      }
    }
  ]
}
```

**Not installable as written:** the repository is hypothetical and the SHA is a fabricated 40-character hexadecimal placeholder, not an observed commit. Replace both before use. The field shapes and marketplace location are documented; keeping the two release labels equal is our proposed policy, not a verified CLI precedence rule.[^7][^13][^20]

### Recommended release steps

1. **Allocate stable identities.** Keep plugin/catalog names equal, use distinctive component IDs, and avoid embedding release numbers in names. Renaming affects settings identity and potentially data handling.[^1][^4][^16]
2. **Prepare a candidate commit.** Set an intentional release label, review changes and record the real full commit SHA. The label communicates compatibility; the SHA identifies source contents.[^6][^7]
3. **Publish to a staging catalog.** Reference that SHA and test installation plus component behaviour in a separate test environment. GitHub's authoring guide recommends both installation and functional checks.[^19]
4. **Promote the same SHA to stable.** Use a distinct stable catalog name, not a supposed local alias. Record the catalog revision, plugin SHA, CLI version and external runtime requirements.[^1][^7][^11]
5. **Rehearse rollback.** Restore the previous pin in the catalog and verify the installed result using the appropriate refresh/update or clean-install path. Do not assume an undocumented version-decrement algorithm will perform the rollback.[^10][^18]
6. **Handle renames explicitly.** Audit settings and persistent data before changing identity; automatic migration is not documented in the reviewed references.[^1][^16]

Separate staging/stable catalog names are a convention, not a built-in release-channel feature. Avoid assuming that identically named plugins from both catalogs can safely coexist: their skill-invocation disambiguation is unresolved.[^1][^5]

## 6. Cross-client compatibility

Agent Plugins standardises a portable package format, not every client's discovery, installation or component behaviour. Portable components in 1.0 are skills and MCP configuration; other features live in client-specific namespaces.[^3][^21]

```text
my-plugin/
|-- plugin.json
|-- skills/
|   `-- test-runner/SKILL.md
|-- mcp.json
`-- com.github.copilot/
    |-- agents/
    `-- hooks/hooks.json
```

This shortened structure follows the official authoring documentation. Other clients ignore unsupported namespaces, and even a client recognising `com.github.copilot` loads only the features it supports.[^3][^21]

| Surface | Format/version distinction |
|---|---|
| Copilot CLI | Current documentation explicitly recognises Agent Plugins 1.0.0 and 1.1.0, plus legacy formats.[^2] |
| VS Code agent plugins | Reviewed authoring documentation explicitly describes Agent Plugins 1.0.0 plus several legacy formats. VS Code acceptance of 1.1.0 was not established.[^21] |
| Conventional VS Code extensions | Use `package.json`, required `publisher` and `name`, a SemVer-compatible `version`, and `engines.vscode`; identity is `${publisher}.${name}`.[^22] |

Do not transfer conventional VS Code extension naming/version requirements to Copilot plugin manifests. Likewise, `com.github.copilot` in an Agent Plugins package is a client namespace, even though the local repository also uses that string as an IntelliJ plugin ID.[^3][^12][^22]

VS Code documents discovery of CLI-installed plugins, but also warns that its Copilot-harness `/plugin` management and Agent Customizations editor have separate inventories. Shared discovery is not proof of unified management.[^23] VS Code settings use `plugin@marketplace`, whereas its plugin installation deep link uses the bare manifest name; conventional extension deep links use yet another form.[^24]

**Recommendation:** test each target client and intended management interface. A recognised package schema does not guarantee that all packaged components work identically.[^21][^23]

## 7. Implications for the current repository

At the inspected commit, the bootstrap:

- Installs Copilot CLI through `mise` with `copilot@latest`.
- Clones or fast-forward-updates [guardian/agentsy](https://github.com/guardian/agentsy) on `main`.
- Runs `agentsy install` without establishing its downstream plugin implementation in this checkout.[^9]

Therefore, pinning a devenv release does not itself freeze the Copilot CLI version or Agentsy checkout used by those steps. This follows directly from their independent moving selectors.[^9]

The repository uses timestamp-style devenv release versions and recommends a `.tool-versions` entry for consuming a selected devenv release. Those are devenv distribution conventions, not evidence of a Copilot plugin versioning policy.[^25]

**Recommended next investigation:** inspect `agentsy install` and its referenced manifests in the owning repository before proposing changes here. If reproducibility is the objective, evaluate CLI/Agentsy pinning alongside plugin source pins, following the existing `mise`/`.tool-versions` convention. No repository changes were made by this research.

## Confidence Assessment

**High confidence:** the documented distinction between package/marketplace/component/client identities; optional Agent Plugins release metadata; full-SHA catalog pinning; command shapes; and the local bootstrap's `latest`/`main` selectors.[^1][^2][^6][^7][^8][^9]

**Moderate confidence:** collision and update behaviour as a description of the running product. These findings are primary-documentation-backed, but no plugin installation, collision experiment or upgrade/downgrade test was performed. Skill precedence has a documented tension, and current CLI/VS Code references differ in stated schema support.[^4][^5][^21]

**Unresolved:** manifest/catalog version precedence; exact update-comparison algorithm; mismatched package/catalog names; identical plugin names across marketplaces; rename migration; and the contents installed by Agentsy. Absence of a documented contract is not proof that a capability is absent.

**Assumptions:** “Copilot plugins” primarily means GitHub Copilot CLI agent plugins, rather than conventional IDE extensions. Organisational release recommendations assume controlled distribution and reproducibility are useful goals, not requirements stated explicitly by the user.

**Evidence limits:** semantic code searches against [github/docs](https://github.com/github/docs) and [github/copilot-cli](https://github.com/github/copilot-cli) returned `401 Requires authentication`. No indexed commit SHA or implementation-level evidence was obtained from those searches, and they were not repeatedly retried. The local repository commit is a separately observed Git SHA, not a semantic-search result. Official web documentation is mutable: references below identify exact pages/sections rather than inventing source-file lines or commit permalinks. Local code citations include full repository paths, line ranges and the observed commit.

## Footnotes

[^1]: GitHub Docs, [Plugin and marketplace reference, “Marketplace catalog”](https://docs.github.com/en/copilot/reference/plugin-and-marketplace-reference#marketplace-catalog), and [CLI plugin reference, “CLI commands”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#cli-commands).
[^2]: GitHub Docs, [CLI plugin reference, “plugin.json”, “Name constraints”, and “Legacy manifest fields”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#pluginjson).
[^3]: Agent Plugins, [“Client extensions”](https://agent-plugins.org/plugin-authors/client-extensions); VS Code, [Agent plugin reference, “Cross-tool compatibility”](https://code.visualstudio.com/docs/agents/reference/agent-plugin-reference#_cross-tool-compatibility).
[^4]: GitHub Docs, [CLI plugin reference, “Loading order and precedence”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#loading-order-and-precedence).
[^5]: GitHub Docs, [CLI command reference, “Skill locations”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-command-reference#skill-locations), [“Skill frontmatter fields”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-command-reference#skill-frontmatter-fields), and [“Commands (alternative skill format)”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-command-reference#commands-alternative-skill-format).
[^6]: Agent Plugins, [Plugin manifest, “Minimal manifest”](https://agent-plugins.org/plugin-authors/manifest#minimal-manifest); GitHub Docs, [“Agent Plugins 1.0 manifest fields”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#agent-plugins-10-manifest-fields) and [“Legacy manifest fields”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#legacy-manifest-fields).
[^7]: GitHub Docs, [CLI plugin reference, “Plugin source types”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#plugin-source-types).
[^8]: GitHub Docs, [CLI plugin reference, “Plugin specification for install command”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#plugin-specification-for-install-command).
[^9]: [core/src/main/resources/com/gu/devenv/modules/githubCopilotPostCreate.sh:10-44](https://github.com/guardian/devenv/blob/59b8287c82271beeaf36336baaab1bc1bd2e01b2/core/src/main/resources/com/gu/devenv/modules/githubCopilotPostCreate.sh#L10-L44).
[^10]: GitHub Docs, [CLI plugin reference, “copilot plugin update options”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#copilot-plugin-update-options).
[^11]: GitHub Docs, [Creating a plugin, “Creating a plugin”](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/plugins-creating#creating-a-plugin), [Adding MCP servers, “Editing the configuration file”](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-mcp-servers#editing-the-configuration-file); Agent Plugins, [MCP servers, “stdio commands and paths”, “Plugin variables”, and “Remote connections”](https://agent-plugins.org/plugin-authors/mcp-servers).
[^12]: [core/src/main/scala/com/gu/devenv/models.scala:114-120](https://github.com/guardian/devenv/blob/59b8287c82271beeaf36336baaab1bc1bd2e01b2/core/src/main/scala/com/gu/devenv/models.scala#L114-L120), [core/src/main/scala/com/gu/devenv/modules/githubCopilot.scala:29-41](https://github.com/guardian/devenv/blob/59b8287c82271beeaf36336baaab1bc1bd2e01b2/core/src/main/scala/com/gu/devenv/modules/githubCopilot.scala#L29-L41), and [core/src/main/scala/com/gu/devenv/Config.scala:90-100](https://github.com/guardian/devenv/blob/59b8287c82271beeaf36336baaab1bc1bd2e01b2/core/src/main/scala/com/gu/devenv/Config.scala#L90-L100).
[^13]: GitHub Docs, [CLI plugin reference, “marketplace.json fields”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#marketplacejson-fields) and [“Plugin entry fields”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#plugin-entry-fields-objects-within-the-plugins-array).
[^14]: GitHub Docs, [CLI command reference, “MCP server loading priority”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-command-reference#mcp-server-loading-priority).
[^15]: GitHub Docs, [Hooks reference, “Hooks locations”](https://docs.github.com/en/copilot/reference/hooks-reference#hooks-locations).
[^16]: GitHub Docs, [CLI plugin reference, “File locations”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#file-locations), and [CLI configuration directory, “plugin-data/”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-config-dir-reference#plugin-data).
[^17]: GitHub Docs, [Hooks configuration reference, “Sandboxed sessions”](https://docs.github.com/en/copilot/reference/hooks-configuration#sandboxed-sessions).
[^18]: GitHub Docs, [CLI plugin reference, “Marketplace subcommands”](https://docs.github.com/en/copilot/reference/copilot-cli-reference/cli-plugin-reference#copilot-plugin-marketplace-alias-marketplaces-subcommands).
[^19]: GitHub Docs, [Creating a plugin, “Test your plugin with GitHub Copilot CLI”](https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/plugins-creating#test-your-plugin-with-github-copilot-cli).
[^20]: GitHub Docs, [Create and share plugin marketplaces, “Create the marketplace repository”](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/create-and-share-plugin-marketplaces#create-the-marketplace-repository).
[^21]: VS Code, [Agent plugin reference, “Plugin formats”, “Package structure”, and “Test and distribute a plugin”](https://code.visualstudio.com/docs/agents/reference/agent-plugin-reference#_plugin-formats).
[^22]: VS Code, [Extension Manifest, “Fields”](https://code.visualstudio.com/api/references/extension-manifest#fields).
[^23]: VS Code, [Agent plugins, “Plugins installed by GitHub Copilot CLI”](https://code.visualstudio.com/docs/agent-customization/agent-plugins#_plugins-installed-by-github-copilot-cli), and [Agent plugin reference, “Plugin management commands”](https://code.visualstudio.com/docs/agents/reference/agent-plugin-reference#_plugin-management-commands).
[^24]: VS Code, [Agent plugin reference, “Workspace plugin settings”](https://code.visualstudio.com/docs/agents/reference/agent-plugin-reference#_workspace-plugin-settings), and [Agent plugins, “Share and install plugins with deep links”](https://code.visualstudio.com/docs/agent-customization/agent-plugins#_share-and-install-plugins-with-deep-links).
[^25]: [.github/workflows/release.yml:31-49](https://github.com/guardian/devenv/blob/59b8287c82271beeaf36336baaab1bc1bd2e01b2/.github/workflows/release.yml#L31-L49), and [README.md:46-54](https://github.com/guardian/devenv/blob/59b8287c82271beeaf36336baaab1bc1bd2e01b2/README.md#L46-L54).
