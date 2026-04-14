<p align="center">
  <h1 align="center">droid-mcp</h1>
  <p align="center">
    Give your Android AI app access to the entire phone.<br/>
    Calendar, contacts, SMS, camera, location, sensors, and 80+ more tools.
  </p>
</p>

<p align="center">
  <a href="https://jitpack.io/#stixez/droid-mcp"><img src="https://jitpack.io/v/stixez/droid-mcp.svg" alt="JitPack" /></a>
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Platform" />
  <img src="https://img.shields.io/badge/min%20SDK-28-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Kotlin-2.1-purple" alt="Kotlin" />
  <img src="https://img.shields.io/badge/tools-91-red" alt="Tools" />
  <img src="https://img.shields.io/badge/license-Apache%202.0-orange" alt="License" />
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
- **For agent builders** — 91 pre-built, validated tools covering the full Android API surface. Skip the boilerplate.

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
    implementation("com.github.stixez.droid-mcp:droid-mcp-core:0.3.0")

    // Pick what you need
    implementation("com.github.stixez.droid-mcp:droid-mcp-calendar:0.3.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-contacts:0.3.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-sms:0.3.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-location:0.3.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-camera:0.3.0")
    // ... see full list below

    // Or include everything
    implementation("com.github.stixez.droid-mcp:droid-mcp-all:0.3.0")
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
    .enableHttpServer(port = 8080, token = "your-secret-token")
    .build()

mcp.startServer()
```

Connect from Claude Code (`~/.claude/settings.json`):

```json
{
  "mcpServers": {
    "my-phone": {
      "type": "http",
      "url": "http://<phone-ip>:8080/mcp",
      "headers": {
        "Authorization": "Bearer your-secret-token"
      }
    }
  }
}
```

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

40 modules, 91 tools. Each module is independent — only the permissions for included modules are added to your manifest.

| Module | Tools | Permissions |
|--------|-------|-------------|
| **core** | MCP protocol, transports | `INTERNET` |
| **device** | `get_device_info` `get_battery_info` `get_connectivity` `get_storage_info` | None |
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
| **alarms** | `create_alarm` `create_timer` `create_reminder` | `SET_ALARM` |
| **settings** | `get_settings` `set_brightness` `set_volume` `toggle_wifi` | `WRITE_SETTINGS` (write) |
| **bluetooth** | `get_bluetooth_status` `list_paired_devices` | `BLUETOOTH_CONNECT` |
| **wifi** | `get_wifi_info` `list_saved_networks` | `ACCESS_WIFI_STATE` |
| **downloads** | `list_downloads` `search_downloads` | None (API 33+) |
| **screen** | `get_screen_state` `get_display_info` | None |
| **tts** | `speak_text` `get_tts_info` | None |
| **web** | `web_search` `fetch_webpage` | `INTERNET` |
| **flashlight** | `toggle_flashlight` `set_flashlight_brightness` | `CAMERA` |
| **network** | `get_data_usage` `get_cellular_signal` `is_vpn_active` | `ACCESS_NETWORK_STATE` |
| **telephony** | `get_phone_number` `get_sim_info` `get_network_operator` `get_call_state` | `READ_PHONE_STATE` |
| **vibration** | `vibrate` `vibrate_pattern` | `VIBRATE` |
| **biometric** | `check_biometric_availability` `get_biometric_enrollments` | None |
| **sensors** | `get_accelerometer` `get_gyroscope` `get_light_level` `get_proximity` | None |
| **qr** | `scan_qr_code` `scan_barcode` `generate_qr_code` | `CAMERA` |
| **camera** | `take_photo` `capture_video` `get_camera_capabilities` | `CAMERA` |
| **audio** | `get_audio_devices` | None |
| **nfc** | `get_nfc_status` `read_nfc_tag` `write_nfc_tag` | `NFC` |
| **intent** | `send_intent` `share_content` `open_deep_link` | None |
| **playback** | `get_now_playing` `media_control` | Notification Listener (special) |
| **screenshot** | `capture_screen` | MediaProjection (special) |
| **dnd** | `get_dnd_status` `set_dnd_mode` | `ACCESS_NOTIFICATION_POLICY` + DND Access (special) |
| **keyguard** | `get_lock_state` `get_keyguard_info` | None |
| **wallpaper** | `get_wallpaper_info` `set_wallpaper` | `SET_WALLPAPER` |
| **ringtone** | `list_ringtones` `get_active_ringtone` `set_ringtone` | `WRITE_SETTINGS` (special, write only) |
| **usb** | `list_usb_devices` `get_usb_device_info` | None |
| **print** | `list_printers` `print_content` | None |

Full parameter reference: [docs/TOOLS.md](docs/TOOLS.md)

### Special permissions

Some modules require permissions that can't be requested at runtime. The tools work without them for read operations, and return clear error messages for write operations that need the grant.

| Module | Permission | How to grant |
|--------|-----------|-------------|
| **playback** | Notification Listener | Settings > Apps > Special access > Notification access |
| **screenshot** | MediaProjection | Host app calls `MediaProjectionManager.createScreenCaptureIntent()` and passes result to `MediaProjectionHolder.set()` |
| **dnd** | DND Access (for `set_dnd_mode`) | Settings > Apps > Special access > Do Not Disturb access |
| **ringtone** | WRITE_SETTINGS (for `set_ringtone`) | Settings > Apps > Special access > Modify system settings |

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
| **Network security** | HTTP server on local network only. Optional Bearer token auth. |
| **No telemetry** | No analytics, crash reporting, or phone-home calls. The `web` module accesses the internet only when explicitly invoked by the LLM. |

---

## Requirements

- Android 9+ (API 28)
- Kotlin 2.0+
- Gradle 8.12+

---

## Sample App

The `sample-app` module includes a Compose UI that registers all 91 tools with quick-test buttons for each one. Categories that require special permissions show a "Grant Access" button that opens the relevant system settings page. Start the HTTP server from the app to connect desktop MCP clients.

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
