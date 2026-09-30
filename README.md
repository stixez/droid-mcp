<p align="center">
  <h1 align="center">droid-mcp</h1>
  <p align="center">
    Give your Android AI app access to the entire phone.<br/>
    Calendar, contacts, SMS, camera, location, sensors, notification reply + push subscription, accessibility-driven UI control, IME typing, floating overlay, shell-UID admin via Shizuku, root-UID admin via libsu, and more — 147 tools across 53 modules.
  </p>
</p>

<p align="center">
  <a href="https://github.com/stixez/droid-mcp/actions/workflows/ci.yml"><img src="https://github.com/stixez/droid-mcp/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="https://jitpack.io/#stixez/droid-mcp"><img src="https://jitpack.io/v/stixez/droid-mcp.svg" alt="JitPack" /></a>
  <a href="https://stixez.github.io/droid-mcp/"><img src="https://img.shields.io/badge/docs-API%20reference-blue" alt="API docs" /></a>
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Platform" />
  <img src="https://img.shields.io/badge/min%20SDK-28-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Kotlin-2.4-purple" alt="Kotlin" />
  <img src="https://img.shields.io/badge/tools-147-red" alt="Tools" />
  <img src="https://img.shields.io/badge/license-Apache%202.0-orange" alt="License" />
  <a href="https://buymeacoffee.com/stixe"><img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?logo=buymeacoffee&logoColor=000" alt="Buy Me a Coffee" /></a>
</p>

---

droid-mcp is an Android SDK that gives LLMs typed, structured access to phone capabilities. It implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) — the same standard used by Claude, Cursor, and other AI tools — so any MCP client can call your phone's APIs over a local network.

It also works without MCP. If you're building an on-device AI app, you can call tools directly as Kotlin functions.

```kotlin
val mcp = DroidMcp.builder()
    .addTools(CalendarTools.all(context))
    .addTools(ContactsTools.all(context))
    .enableHttpServer(context = context)   // optional: serve MCP clients over Wi-Fi
    .build()

// Call a tool directly (e.g. from your on-device LLM's output)
val result = mcp.callTool("read_calendar", mapOf("start_date" to "2026-04-12"))
// result.data = { "events": [...], "count": 3 }

// Or let desktop MCP clients call it at http://<phone-ip>:8080/mcp
mcp.startServer()
```

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
    implementation("com.github.stixez.droid-mcp:droid-mcp-core:0.11.0")

    // Pick what you need
    implementation("com.github.stixez.droid-mcp:droid-mcp-calendar:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-contacts:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-sms:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-location:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-camera:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-mlkit:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-accessibility:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-ime:0.11.0")
    // ... see full list below

    // Or everything except the opt-in modules below
    implementation("com.github.stixez.droid-mcp:droid-mcp-all:0.11.0")

    // Opt-in: shell/root admin tools (pull third-party deps)
    implementation("com.github.stixez.droid-mcp:droid-mcp-shizuku:0.11.0")    // shell-UID admin via Shizuku
    implementation("com.github.stixez.droid-mcp:droid-mcp-root:0.11.0")       // root-UID admin via libsu
}
```

If packaging fails with duplicate `META-INF` files (Netty and BouncyCastle ship copies in several jars), exclude them:

```kotlin
android {
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/license/**",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",  // droid-mcp-tls only
            )
        }
    }
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
        // allowedOrigins = setOf("http://localhost:6274") → allow a browser client (MCP Inspector)
        context = context,        // enables mDNS broadcast on _mcp._tcp
    )
    .toolTimeout(60_000)          // optional; default 5 min per tool call
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

Browser-based clients (e.g. MCP Inspector) send an `Origin` header and must be listed in `allowedOrigins`; native clients like Claude Code don't need this.

### Permission handling

The library never requests permissions. Your app stays in control:

```kotlin
val builder = DroidMcp.builder()
if (CalendarTools.hasPermissions(context)) {
    builder.addTools(CalendarTools.all(context))
} else {
    val needed = CalendarTools.requiredPermissions()  // request these with your own UI
}
```

Some providers register only the tools whose permissions are granted (e.g. `create_event` needs `WRITE_CALENDAR`), so rebuild after a grant.

---

## Modules

53 modules, 147 tools. Each module depends only on `core`, and only the permissions of the modules you include are merged into your manifest. `shizuku` and `root` expose the same 17 shell tools (defined in `shell-core`) through different backends.

Not listed below: the support modules `notification-listener` and `shell-core`, and the [hardening modules](#hardening-modules) `audit`, `tls` and `server-service`. Permissions are what each module's manifest declares; "special" ones are granted in system Settings (see [Special permissions](#special-permissions)).

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
| **media** | `search_media` `get_media_metadata` `list_albums` | `READ_MEDIA_IMAGES` `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` (< API 33) |
| **location** | `get_current_location` `get_location_address` | `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION` |
| **health** | `get_step_count` `get_activity_info` | `ACTIVITY_RECOGNITION` |
| **clipboard** | `read_clipboard` `write_clipboard` | None |
| **apps** | `list_installed_apps` `get_app_info` `launch_app` | None |
| **alarms** | `create_alarm` `create_timer` `create_reminder` | `SET_ALARM`; `create_reminder` also `READ_CALENDAR` `WRITE_CALENDAR` |
| **settings** | `get_settings` `set_brightness` `set_volume` `toggle_wifi` | `ACCESS_WIFI_STATE` `CHANGE_WIFI_STATE`; `set_brightness` needs `WRITE_SETTINGS` (special) |
| **bluetooth** | `get_bluetooth_status` `list_paired_devices` | `BLUETOOTH_CONNECT` (API 31+), `BLUETOOTH` (≤ API 30) |
| **wifi** | `get_wifi_info` `list_saved_networks` | `ACCESS_WIFI_STATE` `ACCESS_NETWORK_STATE` `ACCESS_FINE_LOCATION` |
| **downloads** | `list_downloads` `search_downloads` | `READ_EXTERNAL_STORAGE` (< API 33) |
| **screen** | `get_screen_state` `get_display_info` | None |
| **tts** | `speak_text` `get_tts_info` | None |
| **web** | `web_search` `fetch_webpage` | `INTERNET` |
| **flashlight** | `toggle_flashlight` `set_flashlight_brightness` | None |
| **network** | `get_data_usage` `get_cellular_signal` `is_vpn_active` | `ACCESS_NETWORK_STATE`; `get_data_usage` needs Usage access (special) |
| **telephony** | `get_phone_number` `get_sim_info` `get_network_operator` `get_call_state` | `READ_PHONE_STATE` `READ_PHONE_NUMBERS` |
| **vibration** | `vibrate` `vibrate_pattern` `cancel_vibration` | `VIBRATE` |
| **biometric** | `check_biometric_availability` `get_biometric_enrollments` | `USE_BIOMETRIC` |
| **sensors** | `get_accelerometer` `get_gyroscope` `get_light_level` `get_proximity` | None |
| **qr** | `scan_qr_code` `scan_barcode` `generate_qr_code` | None (decodes image files) |
| **camera** | `take_photo` `capture_video` `get_camera_capabilities` | `CAMERA`, `WRITE_EXTERNAL_STORAGE` (API 28) |
| **audio** | `get_audio_devices` | None |
| **nfc** | `get_nfc_status` `read_nfc_tag` `write_nfc_tag` | `NFC` |
| **intent** | `send_intent` `share_content` `open_deep_link` | None |
| **playback** | `get_now_playing` `media_control` | Notification Listener (special) |
| **notifications-reply** | `list_repliable_notifications` `reply_to_notification` `dismiss_notification` `invoke_notification_action` | Notification Listener (special) |
| **notification-watch** | `watch_notifications` `unwatch_notifications` `list_notification_watches` `poll_notification_watch` (+ `NotificationListenerBus` SharedFlow API) | Notification Listener (special) |
| **accessibility** | `query_screen` `find_node` `wait_for_text` `click_node` `long_click_node` `set_node_text` `scroll_node` `gesture` `global_action` `get_active_window_info` `take_screenshot_via_a11y` `tap` `long_press` `find_and_tap` `scroll_to_find` | Accessibility Service (special) |
| **ime** | `is_ime_active` `type_text` `commit_keystroke` `delete_text` `set_selection` `get_text_around_cursor` `switch_to_previous_ime` | Input Method enabled + selected (special) |
| **overlay** | (programmatic `OverlayController` only — no LLM tools) | `SYSTEM_ALERT_WINDOW` (special) |
| **shizuku** | `install_apk` `uninstall_app` `clear_app_data` `force_stop_app` `disable_app` `enable_app` `grant_permission` `revoke_permission` `list_app_permissions` `put_secure_setting` `put_global_setting` `put_system_setting` `get_top_window` `set_app_standby_bucket` `make_app_inactive` `capture_screen_quiet` `run_shell` | Shizuku running + permission granted (special) |
| **root** | Same 17 tools as `shizuku`, run via `su` | Rooted device + root granted to the host (special) |
| **screenshot** | `capture_screen` | MediaProjection consent (special), `FOREGROUND_SERVICE_MEDIA_PROJECTION` |
| **dnd** | `get_dnd_status` `set_dnd_mode` | `ACCESS_NOTIFICATION_POLICY` + DND Access (special) |
| **keyguard** | `get_lock_state` `get_keyguard_info` | None |
| **wallpaper** | `get_wallpaper_info` `set_wallpaper` | `SET_WALLPAPER` |
| **ringtone** | `list_ringtones` `get_active_ringtone` `set_ringtone` | `set_ringtone` needs `WRITE_SETTINGS` (special) |
| **usb** | `list_usb_devices` `get_usb_device_info` | None |
| **print** | `list_printers` `print_content` | None; `print_content` needs a foreground Activity |
| **mlkit** | `recognize_text` `label_image` `detect_faces` | None (operates on local image files) |

Full parameter reference: [docs/TOOLS.md](docs/TOOLS.md). Generated API docs (KDoc): [stixez.github.io/droid-mcp](https://stixez.github.io/droid-mcp/).

### Hardening modules

Opt-in modules with no LLM tools; the host wires them in. They pull extra dependencies, so they're not in `:droid-mcp-all`.

| Module | API | Pulls |
|--------|-----|-------|
| **audit** | `RoomAuditSink` persists every HTTP `tools/call` to a private Room DB (`recent`, `observe`, `exportJson`, `clear`, retention). Wire it with `Builder.withAuditSink(...)`. | Room |
| **tls** | `SelfSignedCert.loadOrCreate(file)` returns a `TlsConfig` for `Builder.enableTls(...)`. Clients pin `DroidMcp.tlsFingerprint`. | BouncyCastle |
| **server-service** | `DroidMcpServerService`, an abstract foreground service that keeps the HTTP server running while the app is in the background. | androidx.core |

Per-tool gating (`setToolEnabled`, `setDisabledTools`) and token rotation / per-client pairing (`rotateToken`, `pairClient`, `revokeClient`) are built into core. See [docs/SECURITY.md](docs/SECURITY.md).

### Special permissions

These are granted in system Settings, not with a runtime dialog. Tools that need a missing grant return an error saying so.

| Module | Needs | How to grant |
|--------|-------|-------------|
| **playback**, **notifications-reply**, **notification-watch** | Notification listener. For reply/watch, the host's service must extend `McpNotificationListenerServiceBase`. | Settings > Apps > Special access > Notification access |
| **accessibility** | Host service extending `DroidMcpAccessibilityService` | Settings > Accessibility > Installed apps |
| **ime** | Host service extending `DroidMcpInputMethodService`, enabled and selected | Settings > System > Languages & input > On-screen keyboard, then the IME picker |
| **overlay** | Display over other apps | Settings > Apps > Special access > Display over other apps |
| **screenshot** | MediaProjection consent | `MediaProjectionManager.createScreenCaptureIntent()`, then pass the projection to `MediaProjectionHolder.set()` from a `mediaProjection` foreground service |
| **dnd** (`set_dnd_mode`) | Do Not Disturb access | Settings > Apps > Special access > Do Not Disturb access |
| **settings** (`set_brightness`), **ringtone** (`set_ringtone`) | Modify system settings | Settings > Apps > Special access > Modify system settings |
| **network** (`get_data_usage`) | Usage access | Settings > Apps > Special access > Usage access |
| **shizuku** | Shizuku running and permission granted | See [docs/SHIZUKU.md](docs/SHIZUKU.md) |
| **root** | Rooted device, root granted by the superuser manager | See [docs/ROOT.md](docs/ROOT.md) |

Background use: Android 10+ blocks activity launches from the background, so tools that open another app (alarms, `launch_app`, intents, `toggle_wifi`) need the host in the foreground or holding the overlay permission. They return an error otherwise.

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
| **Path sandboxing** | File, ML Kit, QR and wallpaper tools only read inside external storage (canonical-path checked). |
| **Input validation** | Numeric params are clamped to safe ranges; `send_message` validates the phone number. |
| **Permission isolation** | Each module declares only its own permissions. Library never triggers permission requests. |
| **Authentication** | Bearer token required by default, generated with `SecureRandom` if you don't pass one (`DroidMcp.serverToken`). The server listens on all interfaces of the configured port, so only run it on networks you trust (or enable TLS). |
| **HTTP hardening** | Foreign `Origin` headers → 403 (DNS-rebinding / drive-by browser protection; allowlist via `allowedOrigins`), non-JSON → 415, bodies > 4 MB → 413, unknown `MCP-Protocol-Version` → 400. Every request after `initialize` must carry the `Mcp-Session-Id` issued to the same client. |
| **Bounded execution** | Tool calls always run on `Dispatchers.IO` with a per-call timeout (`toolTimeout()`, default 5 min); a cancelled request (or an MCP `notifications/cancelled` from the same client) stops its tool. |
| **SSRF guard** | `fetch_webpage` / `web_search` refuse private, loopback and link-local addresses (re-checked on every redirect) unless `allowPrivateNetwork = true`. |
| **Intent allowlists** | `send_intent` accepts a safe action list; `send_intent` / `open_deep_link` data URIs are limited to http(s), geo, tel, mailto, sms/mms, market. |
| **Read-only mode** | `enableHttpServer(readOnly = true)` lists and accepts only tools that don't change anything. It still allows privacy-sensitive reads (SMS, contacts, screenshots); turn those off one by one with `setToolEnabled`. |
| **Tool annotations** | Tools advertise MCP `readOnlyHint` / `destructiveHint` / `idempotentHint` so clients can decide what to expose. |
| **No telemetry** | droid-mcp's own code makes no analytics or phone-home calls; `web` tools go online only when called. Google ML Kit (used by `mlkit` and `qr`) sends usage metrics to Google under its own terms. |

---

## Requirements

- Android 9+ (API 28) at runtime
- `compileSdk` 36+ and AGP 8.9.1+ in the consuming app (inherited from AndroidX core 1.18)
- Kotlin 2.3+ (the SDK is built with Kotlin 2.4)

---

## Sample App

`sample-app` registers every module (subject to granted permissions) and has these tabs:

- **Tools**: quick-test buttons per category, plus "Grant Access" shortcuts for special permissions
- **Gating**: enable or disable individual tools on the live server
- **Activity**: a log of recent calls
- **Audit**: the persisted `RoomAuditSink` log, with clear and JSON export

Start the HTTP server from the app, then pair a desktop client with the QR code or the displayed token ([docs/PAIRING.md](docs/PAIRING.md)). An HTTPS toggle serves a self-signed cert and shows its fingerprint. The server runs in a foreground service.

---

## Documentation

KDoc API reference (Dokka, rebuilt on every push to `main`): **[stixez.github.io/droid-mcp](https://stixez.github.io/droid-mcp/)**

Guides: [TOOLS](docs/TOOLS.md) · [PAIRING](docs/PAIRING.md) · [SECURITY](docs/SECURITY.md) · [SHIZUKU](docs/SHIZUKU.md) · [ROOT](docs/ROOT.md) · [VERSIONING](docs/VERSIONING.md) · [MIGRATION](docs/MIGRATION-0-TO-1.md)

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
