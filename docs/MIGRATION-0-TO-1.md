# Migrating 0.x → 1.0

**Short version:** 0.4.0 → 0.10.x is a version bump with nothing to change. 0.11.0 fixes a batch of bugs and security gaps, and some of those fixes change behavior clients can observe. Read [0.11.0](#0110) before you upgrade.

This document lists everything that changed across the 0.x line, so you can check whether any of it affects you.

## If you're on 0.4.0

You depend on the original tool modules (calendar, contacts, sms, files, device, …). Through 0.10.x their tool names, parameters, result keys and prose error envelope are unchanged. For 0.11.0, see below.

New surface you *may* opt into (none required):

- **`droid-mcp-notifications-reply`** (0.5.0): reply to / dismiss / invoke actions on notifications.
- **`droid-mcp-accessibility`** (0.6.0): read/click/type/swipe any app.
- **`droid-mcp-ime`** (0.6.0): type into any focused field.
- **Consumer-alignment** (0.7.0): `droid-mcp-notification-watch`, `droid-mcp-overlay`, accessibility composition tools, `PermissionStatus`, short-form error codes.
- **`droid-mcp-shizuku`** (0.8.0) and **`droid-mcp-root`** (0.9.0): shell-UID / root-UID admin tools, opt-in (excluded from `:droid-mcp-all`).
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

## 0.11.0

A correctness and security sweep. Tool names didn't change and nothing was removed. Two tools were added: `cancel_vibration` and `poll_notification_watch`. Each change below is a bug fix, but you'll notice it if your client depended on the old behavior.

### HTTP transport
- Every request after `initialize` must carry the `Mcp-Session-Id` it was issued. Requests without it get 400, and requests with another client's session get 404.
- Requests with an `Origin` header outside `enableHttpServer(allowedOrigins = …)` get 403. Native clients send no `Origin` and are unaffected. Browser clients such as MCP Inspector need their origin allowlisted.
- `POST /mcp` requires `Content-Type: application/json` (otherwise 415) and a body of at most 4 MB (otherwise 413).
- `GET /mcp` returns 405. The old idle SSE stream is gone. Server messages (progress, elicitation) now travel on the `tools/call` reply itself, which becomes an SSE stream only when the tool sends them and the client accepts `text/event-stream`.
- `Authorization` must use the `Bearer` scheme (matched case-insensitively), and a bare token is rejected. `enableHttpServer(token = …)` now requires at least 16 characters.
- `initialize` negotiates `protocolVersion` (2025-11-25, 2025-06-18, 2025-03-26 or 2024-11-05) instead of always answering 2024-11-05.
- Notifications (messages without an `id`) are never answered. Errors use the correct JSON-RPC codes (-32600, -32602, -32603) and always carry `id`.

### Tool results
- `set_node_text`: `text` is now only the replacement value. To select a node by its current content, use the new `match_text`. Previously `text` was also the selector, so the tool only matched fields that already contained the new text.
- `get_sim_info`: `slot_index` is now the physical SIM slot; before, it held the subscription ID. The subscription ID moves to the new `subscription_id` key. Unknown values are `-1` instead of `0`.
- `check_biometric_availability`: `hardware_type` reports authenticator classes (`biometric_strong`, `biometric_weak`, `none`, `update_required`, `unknown`) instead of guessed sensor names.
- `take_screenshot_via_a11y` now defaults to JPEG, downscaled to 1280 px. `take_photo`'s `image_data` is downscaled the same way, while the gallery file stays full resolution.
- `open_deep_link` and `send_intent` accept only http(s), geo, tel, mailto, sms/smsto, mms/mmsto and market URIs, so app-specific schemes such as `spotify:` are rejected. `send_intent` drops PICK, GET_CONTENT, OPEN_DOCUMENT and CREATE_DOCUMENT, whose results were always discarded.
- `get_text_around_cursor` refuses password fields.
- `get_current_location` requests a fresh fix (up to 10 s) when nothing is cached, instead of failing; new `source` key (`cache`/`fresh`).
- Image tools (`take_screenshot_via_a11y`, `capture_screen_quiet`, `take_photo` with `return_data`, `generate_qr_code`) return the picture as an MCP `image` content block. Over HTTP the base64 key (`image_base64`, `image_data`, `qr_image`) no longer appears in the JSON text or `structuredContent`. In-process results are unchanged.
- `notifications/cancelled` now stops the caller's in-flight `tools/call`. The cancelled request gets no response (HTTP 202).

### Tools that used to claim success when nothing happened now return errors
- `send_message` waits for the carrier result: a reported failure is a tool error (`SMS send failed: <reason>`); otherwise `status` is `sent`, or `timeout` (`sent: false`, not an error, since the message may still arrive; don't resend automatically).
- `print_content` needs an Activity. Pass one with `PrintTools.all(context) { currentActivity }`.
- When the host is in the background and lacks the overlay permission, Android 10+ silently drops activity launches. The affected tools now return an error instead of claiming success:
  - The intent tools return the code `background_activity_launch_blocked`.
  - `create_alarm`, `create_timer`, `launch_app`, `toggle_wifi` and `set_brightness` return a prose error, because these 0.4.0 tools keep their original error envelope.
- `set_dnd_mode` reads the mode back and reports `actual_mode`. On Android 15+ it only controls the app's own implicit rule.

### Shell (Shizuku / root)
- `ShellAllowlist` entries match argv token by token, so `"am start"` no longer matches `am startservice`. Entries that start with an interpreter (`sh`, `toybox`, `su`, `app_process`, `env`, …) throw.
- `install_apk` requires an absolute path ending in `.apk`.
- The shell providers default to `ShellPolicy.RECOMMENDED`. `put_*_setting` refuses accessibility, notification-listener, IME, ADB and developer-option keys, and `grant_permission` refuses development permissions such as `WRITE_SECURE_SETTINGS`. Pass `ShellPolicy.PERMISSIVE` to restore the old behavior.
- Root opens one `su` session per call. Some superuser managers log each session.

### Build
- Consumers need `compileSdk` 36+, AGP 8.9.1+ and Kotlin 2.3+.
- Source-compatible with 0.10, not binary-compatible: `ToolParameter` and `ToolResult` gained constructor parameters. Recompile code (including your own libraries) that builds these classes against 0.11.0.

## What 1.0 freezes

At 1.0 the API surface in [VERSIONING.md](VERSIONING.md) is locked: tool names, parameter names, result keys, builder methods, and module coordinates won't change without a major bump. If something in the current surface is going to be renamed, it happens *before* 1.0 — so if you're reading this between 0.10.0 and 1.0 and a name looks wrong, flag it now.
