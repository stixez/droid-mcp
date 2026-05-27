# Versioning Policy

droid-mcp follows [Semantic Versioning 2.0.0](https://semver.org/).

## What the version numbers mean

Given `MAJOR.MINOR.PATCH`:

- **MAJOR** — incompatible API changes. After 1.0, a major bump is the *only* place a breaking change is allowed.
- **MINOR** — new capability, backward-compatible. New modules, new tools, new optional builder methods, new tool parameters with defaults.
- **PATCH** — backward-compatible bug fixes only.

## The API surface covered by semver

These are the contracts a consumer can rely on. After **1.0**, none of them change without a major bump:

- The `McpTool` interface (`name`, `description`, `parameters`, `annotations`, `execute`).
- `ToolResult`, `ToolParameter`, `ParameterType`, `ToolAnnotations` shapes.
- `DroidMcp.Builder` method names and defaults.
- **Per-tool wire contract:** each tool's name, its parameter names, and its result-map keys. Renaming any of these is a breaking change.
- Module Gradle coordinates (`io.droidmcp:droid-mcp-<name>`).
- The MCP protocol behavior (`initialize` / `tools/list` / `tools/call`, error codes, JSON-RPC envelope).

Internal classes (`McpProtocolImpl` internals, transport plumbing, parsing helpers) are **not** part of the contract and may change in any release.

## Pre-1.0 stance

Although semver permits breaking changes in any pre-1.0 release, droid-mcp deliberately keeps breakage at **zero** from 0.4.0 onward. Android's system APIs (ContentResolver, Calendar, Contacts, …) are extremely stable, so tool wire shapes don't need to churn. The only things that could break consumers are *our* renames/merges/removals — and we don't do them. Cleanup items are demoted to "only if there's a concrete reason," never "because it's tidy."

One consequence: the 0.5.0–0.9.0 tool surface never reached a public GitHub release (it sat on a branch). Tools introduced there could adopt short-form error codes from the start; only **0.4.0** tools keep their original human-prose error envelope forever.

## Deprecation cycle

After 1.0:

1. A symbol marked `@Deprecated` keeps working for at least one full minor cycle.
2. The deprecation message names the replacement.
3. Removal happens only at the next major version.

Typealiases kept purely for source compatibility (e.g. `playback.NotificationListenerHolder`) stay indefinitely — the cost is one line, the benefit is zero migration work.

## Distribution and tags

- Releases are published via **JitPack**, keyed off signed git tags (`vMAJOR.MINOR.PATCH`). Maven Central is deferred to post-1.0 and is demand-driven; if it happens, it's a one-line coordinate change for consumers, not a breaking migration.
- Each release has a `## [x.y.z]` section in [CHANGELOG.md](../CHANGELOG.md) with a `compare/` footnote. Where intermediate versions shipped together (0.5–0.9 were cut as one), the footnote spans the actual tag range.

## Support window

The latest minor is supported. Critical security fixes may be backported to the previous minor at the maintainer's discretion. There is no LTS commitment pre-1.0.
