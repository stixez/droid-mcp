# Migrating 0.x → 1.0

**Short version: nothing breaks.** droid-mcp has held a zero-breaking-changes stance since 0.4.0 (see [VERSIONING.md](VERSIONING.md)). Every release from 0.5.0 through the 1.0 hardening sequence is purely additive. Upgrading is a version-bump in your Gradle coordinates.

This document exists to make that promise auditable: it lists everything that changed across the 0.x line so you can confirm none of it affects you.

## If you're on 0.4.0

You depend on the original tool modules (calendar, contacts, sms, files, device, …). All of them are unchanged: same tool names, same parameters, same result keys, same human-prose error envelope. No action needed beyond bumping the version.

New surface you *may* opt into (none required):

- **Tier 1 — `droid-mcp-notifications-reply`** (0.5.0): reply to / dismiss / invoke actions on notifications.
- **Tier 2 — `droid-mcp-accessibility`** (0.6.0): read/click/type/swipe any app.
- **Tier 3 — `droid-mcp-ime`** (0.6.0): type into any focused field.
- **Consumer-alignment** (0.7.0): `droid-mcp-notification-watch`, `droid-mcp-overlay`, accessibility composition tools, `PermissionStatus`, short-form error codes.
- **Tier 4 — `droid-mcp-shizuku`** (0.8.0) and **Tier 5 — `droid-mcp-root`** (0.9.0): shell-UID / root-UID admin tools, opt-in (excluded from `:droid-mcp-all`).
- **Hardening** (0.10.0): per-tool gating, token rotation, per-client pairing, audit hook, TLS, foreground service.

## 0.10.0 specifics

All additive, but worth noting:

- **`DroidMcp.Builder`** gained `withAuditSink(...)` and `enableTls(...)`. Existing builder chains compile and behave identically.
- **`DroidMcp`** gained `setToolEnabled` / `setDisabledTools` / `disabledTools`, `rotateToken` / `pairClient` / `revokeClient` / `pairedClients`, and `tlsFingerprint`. Pure additions.
- **`McpProtocol`** gained an additive `handleMessage(jsonRequest, clientLabel)` overload with a default delegating to the existing single-arg form. If you implemented `McpProtocol` yourself (unusual), you get the overload for free.
- **`HttpTransport.effectiveToken`** changed from a construction-time `val` to a getter so it tracks `rotateToken()`. Same type, same access — no source change.
- **`DROID_MCP_VERSION`** now reports the real release version. It had been frozen at `"0.4.0"` since 0.4.0 (a bug). If you parsed `serverInfo.version` or the mDNS `version` TXT record and hard-coded `"0.4.0"`, update that expectation. This is the one behavioral change in the 0.x line, and it's a correction.

New opt-in modules pull third-party dependencies, so they stay out of `:droid-mcp-all`:

| Module | Dependency | Note |
|--------|-----------|------|
| `droid-mcp-audit` | Room + KSP | Add the KSP plugin in your build if you depend on it. |
| `droid-mcp-tls` | BouncyCastle | Add the `META-INF/versions/9/OSGI-INF/MANIFEST.MF` packaging exclude (see [SECURITY.md](SECURITY.md)). |
| `droid-mcp-server-service` | androidx.core | Declare your concrete service `android:foregroundServiceType="specialUse"`. |

## What 1.0 freezes

At 1.0 the API surface in [VERSIONING.md](VERSIONING.md) is locked: tool names, parameter names, result keys, builder methods, and module coordinates won't change without a major bump. If something in the current surface is going to be renamed, it happens *before* 1.0 — so if you're reading this between 0.10.0 and 1.0 and a name looks wrong, flag it now.
