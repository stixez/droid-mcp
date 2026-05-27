# Changelog

All notable changes to droid-mcp will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [0.10.0] - 2026-05-27

Hardening release. No new capability tiers and no new LLM tools — instead the HTTP transport, the `McpTool` API, and the operational story graduate to something a downstream app can build on through 1.0. Everything is additive: existing tool shapes, the `DroidMcp.Builder` surface, and the wire protocol are unchanged.

### Added
- **Per-tool gating** (`:droid-mcp-core`) — `DroidMcp.setToolEnabled(name, enabled)` / `setDisabledTools(names)` / `disabledTools()`, backed by `ToolRegistry.listEnabledTools()`. A gated tool disappears from `tools/list` and is rejected by `tools/call` (new `tool_disabled` error code) without being unregistered, so a host can drive a checkbox grid without rebuilding the server.
- **Token rotation + per-client pairing** (`:droid-mcp-core`) — new `TokenStore` holds a primary bearer token plus revocable per-client tokens, each tied to an opaque label. `DroidMcp.rotateToken()` mints a fresh primary (paired clients unaffected); `pairClient(label)` / `revokeClient(label)` / `pairedClients()` manage named clients. `HttpTransport.authenticate` now resolves a request to its owning client label (`primary` / the client label / `anonymous` on an open server) for audit attribution. Constant-time token comparison preserved.
- **Audit hook** (`:droid-mcp-core`) — `AuditSink` functional interface + `ToolCallAudit` record (timestamp, tool, client label, arguments JSON, success, error, duration). The protocol records every HTTP `tools/call` after execution and swallows sink exceptions so a broken backend never fails a call. Wire it with `DroidMcp.Builder.withAuditSink(...)`. In-process calls are not audited (the host owns that path).
- **`droid-mcp-audit`** (new, opt-in) — `RoomAuditSink`, a Room-backed `AuditSink` persisting calls to a private on-device DB with fire-and-forget writes, configurable retention (default 7 days), reactive `observe()` browsing, `count()`, `clear()`, and `exportJson()`. Pulls Room + KSP; excluded from `:droid-mcp-all`.
- **TLS** (`:droid-mcp-core`) — `TlsConfig` (keystore + alias + HTTPS port + lazily-computed SHA-256 cert fingerprint). `DroidMcp.Builder.enableTls(config)` binds a Ktor SSL connector on the HTTPS port instead of plaintext; `DroidMcp.tlsFingerprint` exposes the fingerprint to pin in the pairing QR. mDNS advertises `tls=true` and the HTTPS port. Core only *consumes* a keystore — no certificate-building dependency.
- **`droid-mcp-tls`** (new, opt-in) — `SelfSignedCert.loadOrCreate(file)` generates (and persists) a self-signed cert via BouncyCastle into a PKCS12 keystore (the one type Android's runtime supports), returning a `TlsConfig`. Key generation/signing go through the platform providers; no security provider is registered. Excluded from `:droid-mcp-all`.
- **`droid-mcp-server-service`** (new, opt-in) — `DroidMcpServerService`, an abstract foreground service that keeps the HTTP server alive across screen-off / task-killers. Host subclasses it, supplies a configured `DroidMcp` via `createServer()`, declares it `android:foregroundServiceType="specialUse"`, and starts/stops with `DroidMcpServerService.start/stop(...)`. Low-priority ongoing notification. Excluded from `:droid-mcp-all`.
- `docs/SECURITY.md` (threat model), `docs/MIGRATION-0-TO-1.md`, `docs/VERSIONING.md`.
- Sample app: persists every HTTP call through `RoomAuditSink`; opt-in `:droid-mcp-audit` / `:droid-mcp-tls` / `:droid-mcp-server-service` dependencies; BouncyCastle's duplicate OSGi manifest excluded in the packaging block (hosts using `droid-mcp-tls` need the same exclude — see `docs/SECURITY.md`).

### Changed
- **`DROID_MCP_VERSION` corrected to `0.10.0`** — it had been stuck at `0.4.0` through every 0.5–0.9 release (a latent bug; it's broadcast in `serverInfo` and mDNS). `gradle.properties` `VERSION_NAME` and the maven-publish version likewise bumped from their stale `0.4.0` / `0.3.0` values.
- `McpProtocol` gains an additive `handleMessage(jsonRequest, clientLabel)` overload (defaults to the unattributed form) so the transport can attribute audit records. Existing single-arg callers unchanged.
- `HttpTransport.effectiveToken` is now a getter (tracks `rotateToken()`) instead of a construction-time `val`.

### Known limitations
- **TLS handshake is device-verified only.** Cert generation, PKCS12 round-trip, and fingerprint stability are unit-tested on the JVM, but the live Netty TLS handshake on-device is validated manually, not in CI.
- **Dokka API-docs site not yet wired.** The API-freeze docs (SECURITY / MIGRATION / VERSIONING) ship in this release; the generated Dokka site + GitHub Pages publish is the one remaining 0.10.0 doc item.
- **Audit log stores tool arguments in plaintext.** The persisted DB contains whatever the LLM passed (message text, contacts, locations). It lives in the host's private storage; the host owns encryption/retention/deletion. See `docs/SECURITY.md`.

## [0.9.0] - 2026-05-20

Tier 5 (Root) release. Same 17 shell tools as 0.8.0, backed by libsu's `su` shell instead of the Shizuku binder proxy. Strictly more powerful — capable of `/system` writes, freezing apps via `pm hide`, reading `/data/data/<pkg>`, etc. Tool surface is identical (same names, same parameters), so host apps swap backends without re-prompting the LLM.

### Added
- `droid-mcp-root` (new, opt-in) — `RootShellBackend` + `RootTools` provider. Mirrors `:droid-mcp-shizuku` and wires the same `:droid-mcp-shell-core` 17 tools through libsu (`com.github.topjohnwu.libsu:core:5.2.2`).
- `RootTools.requestAccess(onResult)` — async helper that calls `Shell.getShell { ... }` so the superuser manager surfaces its prompt without blocking the UI. Optional callback fires once libsu has the shell ready, with `true` if root was granted. Used by the sample app to re-run ViewModel initialization on grant (no app restart required).
- `RootTools.permissionStatus(context)` — three-way `PermissionStatus`: `Granted` when `Shell.isAppGrantedRoot() == true`; `NotGranted` with "not yet checked" message when null; `NotGranted` with "denied by the superuser manager" message when false.
- `NoOpShellBackend` — always-unavailable `ShellBackend` used by the fallback dispatch pattern documented in `docs/ROOT.md`. When neither root nor Shizuku is reachable, every shell tool errors with `shell_unavailable: NoOp` instead of the host hitting an `UninitializedPropertyAccessException`.
- Sample app: registers `RootTools.all(context)` when root is available (last-write-wins overwrites the Shizuku-routed tools); "Root" ToolsPage category with Grant Access chip + 3 representative tool buttons; `MainActivity` `"root"` special-permission handler calls `RootTools.requestAccess { granted -> ... }` and re-initializes the ViewModel on grant.
- `docs/ROOT.md` — one-page activation guide covering Magisk / KernelSU / SuperSU paths, the Shizuku-vs-Root trade-off table, the "use both with fallback" dispatch pattern, and the "host must not override `Shell.setDefaultBuilder` to a non-root config" caveat.
- Argv quoting via libsu's official `ShellUtils.escapedString(...)` — no custom quoter to maintain or test. Spec-lock at `ShellUtilsEscapeTest` so a future libsu version bump that changes escape semantics surfaces as a regression.

### Changed
- `settings.gradle.kts` now includes the JitPack repository (`https://jitpack.io`) at the `dependencyResolutionManagement` level — libsu is distributed via JitPack (`com.github.topjohnwu.libsu:core`, `:io`). Supply-chain note for consumers: JitPack is now globally enabled for dependency resolution; no existing module pulls from `com.github.*` group coordinates other than libsu, but worth flagging if you audit transitive sources.
- `:droid-mcp-all` continues to exclude both Shizuku and Root (opt-in policy from 0.8.0). Hosts add `implementation(":droid-mcp-root")` explicitly.
- `ShellBackend` interface gains a binary-safe `execBinary(command, args)` method (defaults to `exec` — Shizuku's exec is already byte-safe). `RootShellBackend` overrides with a temp-file roundtrip via `SuFileInputStream` (libsu's `:io` artifact), because libsu's `Shell.Result.out: List<String>` is UTF-8-decoded and corrupts binary stdout. `CaptureScreenQuietTool` (`capture_screen_quiet`) now calls `gatedExecBinary` so PNG bytes survive both backends.

### Known limitations
- **Tool name collision with Shizuku:** Root and Shizuku tools share names by design (the LLM sees one `force_stop_app`, etc.). Registering both `ShizukuTools.all()` AND `RootTools.all()` overwrites the first in the registry — last-write-wins. Hosts wanting "root preferred, Shizuku fallback" should detect availability and pick at startup; the dispatch pattern is documented in `docs/ROOT.md`.
- **Deep `dumpsys` parsers still deferred** (carried from 0.8.0).

## [0.8.0] - 2026-05-20

Tier 4 (Shizuku) release. Adds shell-UID admin verbs — silent app install/uninstall, force-stop, secure/global/system settings writes, permission grant/revoke, standby bucket control, no-prompt screencap, allowlist-gated run_shell. Activated via the Shizuku app over wireless debugging (Android 11+) or ADB; no root required.

### Added
- `droid-mcp-shell-core` (new) — backend-agnostic `ShellBackend` interface + 17 shell-based tools parameterised by it. Tools: `install_apk`, `uninstall_app`, `clear_app_data`, `force_stop_app`, `disable_app`, `enable_app`, `grant_permission`, `revoke_permission`, `list_app_permissions`, `put_secure_setting`, `put_global_setting`, `put_system_setting`, `get_top_window`, `set_app_standby_bucket`, `make_app_inactive`, `capture_screen_quiet`, `run_shell`. The same module + tool surface will be reused by `droid-mcp-root` (0.9.0) with a `RootShellBackend`.
- `droid-mcp-shizuku` (new) — `ShizukuShellBackend` implementation + `ShizukuTools` provider. Wires shell-core tools through the Shizuku binder. Bundles the `dev.rikka.shizuku:api` + `:provider` v13.1.5 deps.
- `ShellAllowlist` — host-side default-deny allowlist for the `run_shell` escape hatch. The LLM cannot run a shell command unless the host's startup code registers a prefix via `ShellAllowlist.set(...)`. Recommended pattern: `ShellAllowlist.set(setOf("pm ", "am ", "settings ", "dumpsys "))`.
- New short-form error codes: `shell_unavailable`, `shell_permission_denied`, `shell_spawn_failed`, `shell_error`, `invalid_package_name`, `invalid_permission`, `invalid_settings_key`, `invalid_settings_value`, `install_failed`, `uninstall_failed`, `clear_failed`, `force_stop_failed`, `disable_failed`, `enable_failed`, `grant_failed`, `revoke_failed`, `dumpsys_failed`, `settings_put_failed`, `set_bucket_failed`, `set_inactive_failed`, `screencap_failed`, `run_shell_not_enabled`.
- Sample app: ShizukuProvider in manifest with `${applicationId}.shizuku` authority; registers `ShizukuTools.all(context)` in MainViewModel; new "Shizuku" ToolsPage category with Grant Access chip + 5 representative tool buttons. `MainActivity.requestSpecialPermission` handles the `"shizuku"` key — opens Shizuku if installed but not granted, falls back to Play Store install if not installed.
- `docs/SHIZUKU.md` — one-page activation guide covering wireless-debugging setup, ADB fallback, the Sui (root-Shizuku) alternative, and security notes around granting Shizuku access to a host app.

### Known limitations
- **Shizuku reflection shim:** Shizuku v13's `Shizuku.newProcess` is package-private. `ShizukuShellBackend` uses a cached reflection lookup, documented at the call site. The mechanism is fragile across Shizuku-API version bumps; migration to a proper `UserService`-AIDL pattern is tracked as a follow-up.
- **Deep dumpsys parsers deferred:** `get_battery_stats`, `get_proc_stats`, and a `full_notifications` dumpsys are intentionally absent — the raw `dumpsys` output is multi-thousand lines and would blow an LLM's token budget without bespoke per-section parsing. They'll ship in a later release with proper projection.

## [0.7.0] - 2026-05-20

EdgeClaw-driven consumer alignment release. All additions are additive — no breaking changes.

### Added
- `droid-mcp-notification-watch` module: 3 tools (`watch_notifications`, `unwatch_notifications`, `list_notification_watches`) + `NotificationListenerBus.events` SharedFlow on `:droid-mcp-notification-listener`. Push subscription complement to 0.5.0's pull/snapshot reply tools. Filter semantics: case-insensitive substring on `sender_pattern` and `keyword`, AND-combine across fields, fire-once-per-key (optional `fire_on_update`), no replay of pre-existing notifications.
- `droid-mcp-overlay` module: `OverlayController` + `OverlayConfig` data class — floating overlay primitive with click / long-press / drag-end callbacks and optional icon. No LLM tools (host-API only).
- `invoke_notification_action` tool in `droid-mcp-notifications-reply` — trigger a non-reply action (Mark as read, Snooze, Archive, etc.) on an active notification. Strict: exactly one of `action_label` / `action_index`.
- Accessibility composition tools: `tap(x,y)`, `long_press(x,y,duration_ms)`, `find_and_tap(match, match_kind, case_insensitive)` with match_kind ∈ {text, desc, id, class}, `scroll_to_find(match, direction, max_scrolls)`. `direction` uses reading semantics — `"down"` reveals content below the viewport (finger physics hidden in the implementation).
- `wait_for_text` extended with `condition: "text" | "window_change"`. Result shape is now `{ status: "matched" | "timeout", elapsed_ms, ... }` — timeout is a NORMAL outcome, not an error.
- `query_screen` returns nodes **ranked** clickable > has-text > scrollable > rest. When truncated, the most useful nodes are kept (token-bound callers can trust the order).
- `NotificationEvent` data class on `:droid-mcp-notification-listener` with `bigText`, `subText`, `tickerText`, `category`, `channelId`, `groupKey`, `isOngoing`, `isClearable`, split `legacyPriority` + `channelImportance`, `postedAt`, `when` (app-set timestamp), `hasReplyAction`, `actionLabels`.
- `PermissionStatus` sealed class in `:droid-mcp-core` (`Granted` / `NotGranted(intent)` / `Unavailable(requiredApiLevel)`).
- `permissionStatus(context)` + `supportedTools(context)` helpers on every special-access module — accessibility, ime, notifications-reply, notification-watch, playback, overlay.
- `AccessibilityTools.idempotentGlobalActions: Set<String>` exposes the safe-to-retry subset of `global_action` action strings ({back, home, recents, notifications, quick_settings, lock_screen} — `power_dialog` and `screenshot` excluded).
- `ToolResult.error(code, detail)` overload for short-form `code: detail` errors. Wire shape unchanged.
- Short-form error codes on all new tools: `accessibility_not_enabled`, `node_not_found`, `node_not_editable`, `invalid_selector`, `invalid_coords`, `gesture_failed: <reason>`, `scroll_exhausted`, `unknown_action`, `ime_not_active`, `notification_listener_not_enabled`, `invalid_filter`, `watch_not_found`, `notification_not_found`, `action_not_found`, `action_not_invokable: <reason>`, `conflicting_args`, `overlay_permission_denied`.
- Sample app: registers the new tools, adds "Notification Watch" + "Overlay" ToolsPage categories, wires the `"overlay"` Grant Access chip to `ACTION_MANAGE_OVERLAY_PERMISSION`.

### Changed
- `set_node_text` short-circuits with `node_not_editable` when the target node exists but is not an editable field (vs the previous generic "performAction returned false"). Same outcome, more precise error.
- `query_screen` errors switched from human-prose to the short-form `accessibility_not_enabled` code. This tool was added in 0.6.0 which never reached a GitHub release (it sat on the `mcp_updates` branch until being cut as 0.7.0), so no released consumer depends on the prose form. The zero-breaking-changes stance applies to tools shipped in 0.4.0 only.
- `query_screen` ordering is now a documented contract from 0.7.0 onward: clickable > has-text > scrollable > rest. When truncated by `max_nodes`, the highest-ranked nodes are kept.
- `NotificationEvent.actionLabels` preserves positional alignment with `sbn.notification.actions`. Null/blank action titles render as `""` so `action_index = N` and `actionLabels[N]` always refer to the same action.

### Distribution
- All new modules published via JitPack alongside existing modules.

## [0.6.0] - 2026-05-20

### Added
- `droid-mcp-accessibility` module: 11 tools (`query_screen`, `find_node`, `wait_for_text`, `click_node`, `long_click_node`, `set_node_text`, `scroll_node`, `gesture`, `global_action`, `get_active_window_info`, `take_screenshot_via_a11y`) — gives LLMs the universal-app hammer: read any screen, dispatch any click / scroll / gesture, take a no-prompt screenshot
- `droid-mcp-ime` module: 7 tools (`is_ime_active`, `type_text`, `commit_keystroke`, `delete_text`, `set_selection`, `get_text_around_cursor`, `switch_to_previous_ime`) — custom keyboard so LLMs can type into any focused field without relying on `ACTION_SET_TEXT`
- Sample app: declares `McpAccessibilityService` and `McpInputMethodService`; ToolsPage adds "Accessibility" + "IME" categories with Grant Access chips; `MainActivity` routes the chips to the relevant Settings screens
- New `DroidMcpAccessibilityService` and `DroidMcpInputMethodService` abstract base classes; host apps subclass and declare them in their manifest

### Notes
- `take_screenshot_via_a11y` requires Android 11 (API 30) or newer; on older devices use the screenshot module's MediaProjection path
- IME tools all short-circuit with a clear error when the droid-mcp keyboard isn't the active IME; pair with accessibility's `global_action` to switch keyboards programmatically

## [0.5.0] - 2026-05-18

### Added
- `droid-mcp-notifications-reply` module: `list_repliable_notifications`, `reply_to_notification`, `dismiss_notification` — drives the `RemoteInput` reply action on incoming notifications so LLMs can answer WhatsApp, Signal, Messenger, Slack, SMS, Gmail, etc. without per-app integrations
- `droid-mcp-notification-listener` shared support module: `NotificationListenerHolder`, `NotificationStore`, and `McpNotificationListenerServiceBase` — host apps subclass the base service to enable the active-notification cache and `dismiss_notification`
- Sample app: extends `McpNotificationListenerServiceBase`, registers `NotificationsReplyTools`, and adds a "Notifications (Reply)" category on the Tools page

### Changed
- `io.droidmcp.playback.NotificationListenerHolder` is now a `typealias` to the new shared location — existing consumers compile without changes; new code should import `io.droidmcp.notification.NotificationListenerHolder`
- `droid-mcp-playback` now declares `api(project(":droid-mcp-notification-listener"))` so the holder is still reachable transitively

## [0.4.0] - 2026-05-15

### Added
- `droid-mcp-mlkit` module: on-device `recognize_text`, `label_image`, `detect_faces` tools (Google ML Kit, no network)
- mDNS broadcast (`_mcp._tcp`) with version/auth/readonly TXT records so desktop MCP clients can auto-discover the phone on the local network
- QR pairing in sample app — encodes URL, bearer token, and device name in a single scan; payload schema documented in `docs/PAIRING.md`
- MCP tool annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`) applied per category — read/get/list/search are readOnly+idempotent, send/write to external state are destructive, web tools are openWorld; surfaced under each entry in `tools/list`
- `.github/workflows/ci.yml` and `release.yml` for CI builds and automated GitHub Releases from CHANGELOG sections

### Changed
- `HttpTransport` now requires a bearer token by default; one is auto-generated with `SecureRandom` if not supplied and exposed via `DroidMcp.serverToken`
- New `enableHttpServer(readOnly = true)` flag exposes only read-only tools and rejects `tools/call` for destructive tools with an MCP content error (`isError: true`)
- 401 responses include `WWW-Authenticate: Bearer realm="droid-mcp"`
- Sample app displays the bearer token with a copy-to-clipboard button

### Security
- Default-on auth closes accidental exposure of SMS / intents / camera / NFC writes on shared Wi-Fi
- `set_ringtone` no longer accepts `file://` URIs (carried over from 0.3.x review; reaffirmed)
- mDNS does not advertise the bearer token; the token reaches the client out-of-band (QR or copy/paste)

## [0.3.0] - 2026-04-14

### Added
- 10 new modules: NFC, intent, playback, screenshot, DND, keyguard, wallpaper, ringtone, USB, print
- MCP Streamable HTTP transport for Claude Code integration
- JitPack publishing via `jitpack.yml`

## [0.2.0] - 2026-04-12

### Added
- 9 new modules: telephony, vibration, flashlight, biometric, network, sensors, QR, camera, audio
- Sample app integration for all 30 modules

### Fixed
- Resource leaks in cursor handling
- Fully-qualified-name imports cleaned up
- Biometric availability logic
- Scanner cleanup in QR module

## [0.1.0] - 2026-04-12

### Added
- Initial release: core MCP protocol implementation (JSON-RPC 2.0)
- In-process transport for on-device LLMs
- HTTP transport (Ktor) for desktop MCP clients
- Modules: device, calendar, contacts, SMS, files, notifications, call log, media, location, health, clipboard, apps, alarms, settings, bluetooth, wifi, downloads, screen, TTS, web, all
- Sample app with Compose UI

<!--
    0.5.0 through 0.8.0 were developed on the `mcp_updates` branch and not
    cut as separate GitHub releases — all four bodies of work ship together
    in 0.9.0. The footnote below therefore compares against v0.4.0 directly.
    If a future cut backfills intermediate tags, add footnotes for them.
-->
[0.10.0]: https://github.com/stixez/droid-mcp/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/stixez/droid-mcp/compare/v0.4.0...v0.9.0
[0.4.0]: https://github.com/stixez/droid-mcp/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/stixez/droid-mcp/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/stixez/droid-mcp/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/stixez/droid-mcp/releases/tag/v0.1.0
