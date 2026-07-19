<p align="center">
  <h1 align="center">droid-mcp</h1>
  <p align="center">
    Give your Android AI app access to the entire phone.<br/>
    Calendar, contacts, SMS, camera, location, sensors, notification reply + push subscription, accessibility-driven UI control, IME typing, floating overlay, shell-UID admin via Shizuku, root-UID admin via libsu, and more — 145 tools across 53 modules.
  </p>
</p>

<p align="center">
  <a href="https://github.com/stixez/droid-mcp/actions/workflows/ci.yml"><img src="https://github.com/stixez/droid-mcp/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="https://jitpack.io/#stixez/droid-mcp"><img src="https://jitpack.io/v/stixez/droid-mcp.svg" alt="JitPack" /></a>
  <a href="https://stixez.github.io/droid-mcp/"><img src="https://img.shields.io/badge/docs-API%20reference-blue" alt="API docs" /></a>
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Platform" />
  <img src="https://img.shields.io/badge/min%20SDK-28-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Kotlin-2.1-purple" alt="Kotlin" />
  <img src="https://img.shields.io/badge/tools-145-red" alt="Tools" />
  <img src="https://img.shields.io/badge/license-Apache%202.0-orange" alt="License" />
  <a href="https://buymeacoffee.com/stixe"><img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?logo=buymeacoffee&logoColor=000" alt="Buy Me a Coffee" /></a>
</p>

---

droid-mcp is an Android SDK that gives LLMs typed, structured access to phone capabilities. It implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) — the same standard used by Claude, Cursor, and other AI tools — so any MCP client can call your phone's APIs over a local network.

It also works without MCP. If you're building an on-device AI app, you can call tools directly as Kotlin functions.

```kotlin
// Build with the modules you need
val mcp = DroidMcp.builder()
    .addTools(CalendarTools.all(context))
    .addTools(ContactsTools.all(context))
    .addTools(LocationTools.all(context))
    .build()

// Your LLM decides to read the calendar
val result = mcp.callTool("read_calendar", mapOf("start_date" to "2026-04-12"))
// result.data = { "events": [...], "count": 3 }

// Or start an HTTP server and connect Claude Code over WiFi
mcp.startServer()
// Connect from desktop: http://<phone-ip>:8080/mcp
```

---

## Why

On-device LLMs and AI agents are getting good, but they can't do much without access to the phone. droid-mcp bridges that gap:

- **For on-device LLM apps** — call tools directly from your model's output. No server needed.
- **For desktop AI tools** — connect Claude Code, Cursor, or any MCP client to your phone over WiFi.
- **For agent builders** — 145 pre-built, validated tools covering the full Android API surface. Skip the boilerplate.

---

## Installation

Add the modules you need:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    // Core (required)
    implementation("com.github.stixez.droid-mcp:droid-mcp-core:0.10.1")

    // Pick what you need
    implementation("com.github.stixez.droid-mcp:droid-mcp-calendar:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-contacts:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-sms:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-location:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-camera:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-mlkit:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-accessibility:0.10.1")
    implementation("com.github.stixez.droid-mcp:droid-mcp-ime:0.10.1")
    // ... see full list below

    // Or include everything (except Tier 4/5 power-user modules)
    implementation("com.github.stixez.droid-mcp:droid-mcp-all:0.10.1")

    // Power-user tiers — opt in only if you want them (they pull third-party deps)
    implementation("com.github.stixez.droid-mcp:droid-mcp-shizuku:0.10.1")    // Tier 4: shell-UID admin (pulls dev.rikka.shizuku)
    implementation("com.github.stixez.droid-mcp:droid-mcp-root:0.10.1")       // Tier 5: root-UID admin (pulls libsu)
}
```

---

## Usage

### On-device (direct calls)

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .addTools(CalendarTools.all(context))
    .addTools(SmsTools.all(context))
    .build()

// Get tool definitions as JSON — pass this to your LLM
val toolsJson = mcp.listToolsJson()

// Execute whatever tool the LLM picks
val result = mcp.callTool("send_message", mapOf(
    "to" to "+1234567890",
    "body" to "On my way!"
))

if (result.isSuccess) {
    val data = result.data  // Map<String, Any?>
} else {
    val error = result.errorMessage
}
```

### Desktop connection (MCP over HTTP)

Start the server on the phone:

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .addTools(CalendarTools.all(context))
    .enableHttpServer(
        port = 8080,
        // token = null → auto-generated via SecureRandom (read it from mcp.serverToken)
        // readOnly = true → hide destructive tools from clients
        context = context,        // enables mDNS broadcast on _mcp._tcp
    )
    .build()

mcp.startServer()
val token = mcp.serverToken  // share via QR / pairing
```

Connect from Claude Code (`~/.claude/settings.json`):

```json
{
  "mcpServers": {
    "my-phone": {
      "type": "http",
      "url": "http://<phone-ip>:8080/mcp",
      "headers": {
        "Authorization": "Bearer <token-from-mcp.serverToken>"
      }
    }
  }
}
```

The phone broadcasts itself on the local network via mDNS (`_mcp._tcp`) — the sample app's pairing QR encodes the URL and token, so clients can scan and connect without copying the IP by hand.

Now Claude can read your calendar, search contacts, check battery level, and use any other registered tool — directly from your terminal.

### Permission handling

The library never requests permissions. Your app stays in control:

```kotlin
if (CalendarTools.hasPermissions(context)) {
    mcp.addTools(CalendarTools.all(context))
} else {
    val needed = CalendarTools.requiredPermissions()
    // Show your own permission UI
}
```

---

## Modules

53 modules, 145 tools. Each module is independent — only the permissions for included modules are added to your manifest. Root (Tier 5) reuses the same 17 shell tools as Shizuku (Tier 4) via the shared `ShellBackend` interface.

The table below lists 48 of them (`core` plus the tool modules; `overlay` is listed too, though it exposes a programmatic API rather than LLM tools). The remaining five are infrastructure: two support modules (`notification-listener`, `shell-core`) that the listener-based and shell-based modules wire against, plus three opt-in hardening modules added in 0.10.0 (`audit`, `tls`, `server-service`) — see [Hardening modules](#hardening-modules-0100) below.

| Module | Tools | Permissions |
|--------|-------|-------------|
| **core** | MCP protocol, transports | `INTERNET` |
| **device** | `get_device_info` `get_battery_info` `get_connectivity` `get_storage_info` | `ACCESS_NETWORK_STATE` |
| **calendar** | `read_calendar` `create_event` `search_events` | `READ_CALENDAR` `WRITE_CALENDAR` |
| **contacts** | `search_contacts` `read_contact` `list_contacts` | `READ_CONTACTS` |
| **sms** | `read_messages` `send_message` `search_messages` | `READ_SMS` `SEND_SMS` |
| **files** | `browse_files` `read_file` `search_files` | `READ_EXTERNAL_STORAGE` (< API 33) |
| **notifications** | `get_active_notifications` | None |
| **calllog** | `read_call_log` `search_call_log` | `READ_CALL_LOG` |
| **media** | `search_media` `get_media_metadata` `list_albums` | `READ_MEDIA_IMAGES` `READ_MEDIA_VIDEO` |
| **location** | `get_current_location` `get_location_address` | `ACCESS_FINE_LOCATION` |
| **health** | `get_step_count` `get_activity_info` | `ACTIVITY_RECOGNITION` |
| **clipboard** | `read_clipboard` `write_clipboard` | None |
| **apps** | `list_installed_apps` `get_app_info` `launch_app` | None |
| **alarms** | `create_alarm` `create_timer` `create_reminder` | `SET_ALARM` `READ_CALENDAR` `WRITE_CALENDAR` |
| **settings** | `get_settings` `set_brightness` `set_volume` `toggle_wifi` | `WRITE_SETTINGS` (write) `CHANGE_WIFI_STATE` |
| **bluetooth** | `get_bluetooth_status` `list_paired_devices` | `BLUETOOTH_CONNECT` `BLUETOOTH` |
| **wifi** | `get_wifi_info` `list_saved_networks` | `ACCESS_WIFI_STATE` `ACCESS_NETWORK_STATE` `ACCESS_FINE_LOCATION` |
| **downloads** | `list_downloads` `search_downloads` | `READ_EXTERNAL_STORAGE` (< API 33) |
| **screen** | `get_screen_state` `get_display_info` | None |
| **tts** | `speak_text` `get_tts_info` | None |
| **web** | `web_search` `fetch_webpage` | `INTERNET` |
| **flashlight** | `toggle_flashlight` `set_flashlight_brightness` | `CAMERA` `FLASHLIGHT` |
| **network** | `get_data_usage` `get_cellular_signal` `is_vpn_active` | `ACCESS_NETWORK_STATE` + `PACKAGE_USAGE_STATS` (special, for `get_data_usage`) |
| **telephony** | `get_phone_number` `get_sim_info` `get_network_operator` `get_call_state` | `READ_PHONE_STATE` `READ_SMS` |
| **vibration** | `vibrate` `vibrate_pattern` | `VIBRATE` |
| **biometric** | `check_biometric_availability` `get_biometric_enrollments` | None |
| **sensors** | `get_accelerometer` `get_gyroscope` `get_light_level` `get_proximity` | None |
| **qr** | `scan_qr_code` `scan_barcode` `generate_qr_code` | `CAMERA` |
| **camera** | `take_photo` `capture_video` `get_camera_capabilities` | `CAMERA` |
| **audio** | `get_audio_devices` | None |
| **nfc** | `get_nfc_status` `read_nfc_tag` `write_nfc_tag` | `NFC` |
| **intent** | `send_intent` `share_content` `open_deep_link` | None |
| **playback** | `get_now_playing` `media_control` | Notification Listener (special) |
| **notifications-reply** | `list_repliable_notifications` `reply_to_notification` `dismiss_notification` `invoke_notification_action` | Notification Listener (special) |
| **notification-watch** | `watch_notifications` `unwatch_notifications` `list_notification_watches` (+ `NotificationListenerBus` SharedFlow API) | Notification Listener (special) |
| **accessibility** | `query_screen` `find_node` `wait_for_text` `click_node` `long_click_node` `set_node_text` `scroll_node` `gesture` `global_action` `get_active_window_info` `take_screenshot_via_a11y` `tap` `long_press` `find_and_tap` `scroll_to_find` | Accessibility Service (special) |
| **ime** | `is_ime_active` `type_text` `commit_keystroke` `delete_text` `set_selection` `get_text_around_cursor` `switch_to_previous_ime` | Input Method enabled + selected (special) |
| **overlay** | (programmatic `OverlayController` only — no LLM tools) | `SYSTEM_ALERT_WINDOW` (special) |
| **shizuku** | `install_apk` `uninstall_app` `clear_app_data` `force_stop_app` `disable_app` `enable_app` `grant_permission` `revoke_permission` `list_app_permissions` `put_secure_setting` `put_global_setting` `put_system_setting` `get_top_window` `set_app_standby_bucket` `make_app_inactive` `capture_screen_quiet` `run_shell` | Shizuku service running + permission granted (special). Backed by `ShellBackend` interface shared with `:droid-mcp-root`. |
| **root** | (same 17 tools as `shizuku`, routed via `su` instead of Shizuku binder) | Device rooted + host granted root via superuser manager (Magisk / KernelSU / SuperSU). Strictly more powerful: `/system` writes, `pm hide`, `/data/data/<pkg>` access. |
| **screenshot** | `capture_screen` | MediaProjection (special) |
| **dnd** | `get_dnd_status` `set_dnd_mode` | `ACCESS_NOTIFICATION_POLICY` + DND Access (special) |
| **keyguard** | `get_lock_state` `get_keyguard_info` | None |
| **wallpaper** | `get_wallpaper_info` `set_wallpaper` | `SET_WALLPAPER` |
| **ringtone** | `list_ringtones` `get_active_ringtone` `set_ringtone` | `WRITE_SETTINGS` (special, write only) |
| **usb** | `list_usb_devices` `get_usb_device_info` | None |
| **print** | `list_printers` `print_content` | None |
| **mlkit** | `recognize_text` `label_image` `detect_faces` | None (operates on local image files) |

Full parameter reference: [docs/TOOLS.md](docs/TOOLS.md). Generated API docs (KDoc): [stixez.github.io/droid-mcp](https://stixez.github.io/droid-mcp/).

### Hardening modules (0.10.0)

Three opt-in infrastructure modules harden the HTTP transport and operations. They expose no LLM tools — they're programmatic APIs the host wires in. Each pulls a third-party dependency, so all three stay outside `:droid-mcp-all`.

| Module | API | Dependency |
|--------|-----|-----------|
| **audit** | `RoomAuditSink` — persists every HTTP `tools/call` to a private Room DB (retention, `observe()`, `exportJson()`, `clear()`). Wire via `DroidMcp.Builder.withAuditSink(...)`. The dependency-free `AuditSink` hook itself lives in core. | Room + KSP |
| **tls** | `SelfSignedCert.loadOrCreate(file)` → a `TlsConfig` for `DroidMcp.Builder.enableTls(...)`. Self-signed cert; clients pin `DroidMcp.tlsFingerprint`. | BouncyCastle |
| **server-service** | `DroidMcpServerService` — abstract foreground service keeping the HTTP server alive across screen-off / task-killers. | androidx.core |

Per-tool gating (`DroidMcp.setToolEnabled` / `setDisabledTools`) and token rotation / per-client pairing (`rotateToken` / `pairClient` / `revokeClient`) are built into **core** — no extra module. See [docs/SECURITY.md](docs/SECURITY.md) for the threat model.

### Capability tiers

Modules are grouped into five tiers by the kind of permission they need. **Tiers 1–3 are the core surface; Tiers 4–5 are opt-in power tools** for hosts that need shell-UID or root-UID admin — they pull third-party native dependencies and stay outside `:droid-mcp-all` to keep the default APK lean.

| Tier | What | Modules |
|---|---|---|
| **1. Runtime / install-time perms** | Standard `<uses-permission>` grants | `device` `calendar` `contacts` `sms` `files` `media` `location` `calllog` `health` `apps` `alarms` `settings` `bluetooth` `wifi` `downloads` `screen` `tts` `web` `flashlight` `network` `telephony` `vibration` `biometric` `sensors` `qr` `camera` `audio` `nfc` `intent` `clipboard` `notifications` `keyguard` `wallpaper` `usb` `print` `mlkit` |
| **2. Notification Listener** | Settings > Apps > Special access > Notification access | `playback` `notifications-reply` `notification-watch` |
| **3. Accessibility / IME / Overlay / DND / Ringtone / Screenshot** | Per-feature Settings toggles | `accessibility` `ime` `overlay` `dnd` `ringtone` `screenshot` |
| **4. Shell-UID (opt-in)** | Shizuku activated + permission granted to host app | `shizuku` (pulls `dev.rikka.shizuku`; see [docs/SHIZUKU.md](docs/SHIZUKU.md)) |
| **5. Root-UID (opt-in)** | Device rooted + superuser-manager grant | `root` (pulls `com.github.topjohnwu.libsu`; see [docs/ROOT.md](docs/ROOT.md)) |

### Special permissions

Some modules require permissions that can't be requested at runtime. The tools work without them for read operations, and return clear error messages for write operations that need the grant.

| Module | Permission | How to grant |
|--------|-----------|-------------|
| **playback** | Notification Listener | Settings > Apps > Special access > Notification access |
| **notifications-reply** | Notification Listener (service must extend `McpNotificationListenerServiceBase`) | Settings > Apps > Special access > Notification access |
| **notification-watch** | Notification Listener (shares listener service with `notifications-reply`) | Settings > Apps > Special access > Notification access |
| **accessibility** | Accessibility Service (service must extend `DroidMcpAccessibilityService`) | Settings > Accessibility > Installed apps |
| **ime** | Input Method enabled + selected (service must extend `DroidMcpInputMethodService`) | Settings > System > Languages & input > On-screen keyboard, plus the IME picker |
| **overlay** | Draw over other apps | Settings > Apps > Special access > Display over other apps |
| **shizuku** | Shizuku service running + permission granted to host app | Install Shizuku, activate via wireless debugging (Android 11+) or ADB, grant the runtime permission. See [docs/SHIZUKU.md](docs/SHIZUKU.md). |
| **root** | Device rooted + superuser manager grants root to host app | Root via Magisk / KernelSU / SuperSU; on first `Shell.cmd(...)` call the manager surfaces a permission prompt. See [docs/ROOT.md](docs/ROOT.md). |
| **screenshot** | MediaProjection | Host app calls `MediaProjectionManager.createScreenCaptureIntent()` and passes result to `MediaProjectionHolder.set()` |
| **dnd** | DND Access (for `set_dnd_mode`) | Settings > Apps > Special access > Do Not Disturb access |
| **ringtone** | WRITE_SETTINGS (for `set_ringtone`) | Settings > Apps > Special access > Modify system settings |
| **network** | PACKAGE_USAGE_STATS (for `get_data_usage`) | Settings > Apps > Special access > Usage access |

---

## Architecture

```
                    Your App
                       |
              +--------+--------+
              |                 |
        On-device LLM    Desktop MCP Client
              |              (Claude Code, etc.)
              v                 |
        InProcessTransport      v
              |            HttpTransport
              |           (Ktor/Netty)
              +--------+--------+
                       |
                  ToolRegistry
                       |
     +-----+-----+----+----+-----+-----+
     |     |     |    |    |     |     |
   Device Cal  SMS  Files Loc  Camera ...
   Tools  Tools Tools Tools Tools Tools
     |     |     |    |    |     |     |
     Android System APIs
```

---

## Safety

| Feature | How |
|---------|-----|
| **Path sandboxing** | File tools restricted to external storage. System paths rejected. |
| **Input validation** | All params validated and clamped. Phone numbers checked before SMS send. |
| **Permission isolation** | Each module declares only its own permissions. Library never triggers permission requests. |
| **Network security** | HTTP server on local network only. Bearer token auth required by default — auto-generated via `SecureRandom` if not supplied (accessible via `DroidMcp.serverToken`). |
| **Read-only mode** | `enableHttpServer(readOnly = true)` filters destructive tools from `tools/list` and rejects `tools/call` for non-read-only tools. |
| **Tool annotations** | Every tool advertises `readOnlyHint` / `destructiveHint` / `idempotentHint` per MCP spec so clients can decide which to expose. |
| **No telemetry** | No analytics, crash reporting, or phone-home calls. The `web` module accesses the internet only when explicitly invoked by the LLM. |

---

## Requirements

- Android 9+ (API 28)
- Kotlin 2.0+
- Gradle 8.12+

---

## Sample App

The `sample-app` module includes a Compose UI that exercises the full tool surface — every module is registered (subject to permission availability) with quick-test buttons for representative tools in each category. Categories that require special permissions show a "Grant Access" button that opens the relevant system settings page. Start the HTTP server from the app to connect desktop MCP clients — pair via the QR code or copy the bearer token shown on the home screen. See [docs/PAIRING.md](docs/PAIRING.md).

It also demonstrates the 0.10.0 hardening features end-to-end:

- **Tools** tab — quick-test buttons per category.
- **Gating** tab — a per-tool switch grid (with filter + bulk enable/disable) that toggles tools off the live `tools/list` / `tools/call` surface without restarting the server.
- **Audit** tab — browse the persisted `RoomAuditSink` log of HTTP calls (tool, client, outcome, duration, arguments), clear it, or export to JSON.
- **TLS** toggle — serve over HTTPS with a self-signed cert; the SHA-256 fingerprint is shown (copyable) and folded into the pairing QR for the client to pin.
- The server runs inside a **foreground service** (`DroidMcpServerService`) so it survives screen-off / backgrounding.

---

## Documentation

Full KDoc API reference for every module, generated with Dokka and published on every push to `main`: **[stixez.github.io/droid-mcp](https://stixez.github.io/droid-mcp/)**

See also: [docs/PAIRING.md](docs/PAIRING.md) (mDNS/QR pairing), [docs/SECURITY.md](docs/SECURITY.md), [docs/VERSIONING.md](docs/VERSIONING.md), [docs/SHIZUKU.md](docs/SHIZUKU.md), [docs/ROOT.md](docs/ROOT.md), [docs/MIGRATION-0-TO-1.md](docs/MIGRATION-0-TO-1.md).

---

## Contributing

Contributions welcome. Please open an issue before submitting a PR for non-trivial changes.

## License

```
Copyright 2026

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
