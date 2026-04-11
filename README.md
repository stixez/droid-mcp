<p align="center">
  <h1 align="center">droid-mcp</h1>
  <p align="center">
    Android MCP SDK — 50 tools across 22 modules<br/>
    Add phone capabilities to your Android AI app in one line.
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android-green" alt="Platform" />
  <img src="https://img.shields.io/badge/min%20SDK-28-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Kotlin-2.1-purple" alt="Kotlin" />
  <img src="https://img.shields.io/badge/license-Apache%202.0-orange" alt="License" />
</p>

---

## Overview

droid-mcp is an open-source Android SDK that exposes device capabilities through the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). It gives LLMs structured, typed access to Android system APIs — calendar, contacts, SMS, files, location, media, and more — through a standard tool-calling interface.

It supports both in-process tool calls for on-device LLMs and an HTTP transport for connecting desktop MCP clients (like Claude Code) over a local network.

**Key features:**

- **48 tools across 21 modules** — cover the full range of Android system APIs.
- **Modular** — include only the capabilities your app needs. Each module is an independent Gradle artifact.
- **Standard MCP protocol** — compatible with any MCP client, on-device or remote.
- **Built-in safety** — input validation, path sandboxing, and permission isolation throughout.

---

## Getting Started

### Installation

Add the modules you need to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core is always required
    implementation("io.droidmcp:core:0.1.0")

    // Add the capabilities you need
    implementation("io.droidmcp:device:0.1.0")
    implementation("io.droidmcp:calendar:0.1.0")
    implementation("io.droidmcp:contacts:0.1.0")
    implementation("io.droidmcp:sms:0.1.0")
    implementation("io.droidmcp:files:0.1.0")
    implementation("io.droidmcp:media:0.1.0")
    implementation("io.droidmcp:location:0.1.0")

    // Or include everything at once
    // implementation("io.droidmcp:all:0.1.0")
}
```

### Initialization

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .addTools(CalendarTools.all(context))
    .addTools(ContactsTools.all(context))
    .build()
```

### Calling Tools

```kotlin
// Get tool definitions as JSON (for your LLM prompt)
val toolsJson = mcp.listToolsJson()

// Execute a tool
val result = mcp.callTool("read_calendar", mapOf(
    "start_date" to "2026-04-10",
    "end_date" to "2026-04-17"
))

if (result.isSuccess) {
    val data = result.data   // Map containing "events" and "count"
} else {
    val error = result.errorMessage
}
```

### Permission Handling

The library does not request permissions. Each module provides helpers so your app can manage the permission UX:

```kotlin
if (CalendarTools.hasPermissions(context)) {
    droidMcp.addTools(CalendarTools.all(context))
} else {
    val needed = CalendarTools.requiredPermissions()
    // Request permissions through your app's UI
}
```

---

## Modules

Each module is an independent Gradle artifact. Only the permissions for included modules are added to your app's manifest.

| Module | Tools | Permissions |
|--------|-------|-------------|
| **`droid-mcp-core`** | MCP protocol, in-process + HTTP transports | `INTERNET` |
| **`droid-mcp-device`** | `get_device_info` `get_battery_info` `get_connectivity` `get_storage_info` | None |
| **`droid-mcp-calendar`** | `read_calendar` `create_event` `search_events` | `READ_CALENDAR` `WRITE_CALENDAR` |
| **`droid-mcp-contacts`** | `search_contacts` `read_contact` `list_contacts` | `READ_CONTACTS` |
| **`droid-mcp-sms`** | `read_messages` `send_message` `search_messages` | `READ_SMS` `SEND_SMS` |
| **`droid-mcp-files`** | `browse_files` `read_file` `search_files` | `READ_EXTERNAL_STORAGE` (API < 33) / None (API 33+) |
| **`droid-mcp-notifications`** | `get_active_notifications` | None |
| **`droid-mcp-calllog`** | `read_call_log` `search_call_log` | `READ_CALL_LOG` |
| **`droid-mcp-media`** | `search_media` `get_media_metadata` `list_albums` | `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` `READ_MEDIA_VIDEO` (API 33+) |
| **`droid-mcp-location`** | `get_current_location` `get_location_address` | `ACCESS_FINE_LOCATION` `ACCESS_COARSE_LOCATION` |
| **`droid-mcp-health`** | `get_step_count` `get_activity_info` | `ACTIVITY_RECOGNITION` (API 29+) |
| **`droid-mcp-clipboard`** | `read_clipboard` `write_clipboard` | None |
| **`droid-mcp-apps`** | `list_installed_apps` `get_app_info` `launch_app` | None |
| **`droid-mcp-alarms`** | `create_alarm` `create_timer` `create_reminder` | `SET_ALARM` |
| **`droid-mcp-settings`** | `get_settings` `set_brightness` `set_volume` `toggle_wifi` | None (read) / `WRITE_SETTINGS` (write) / `CHANGE_WIFI_STATE` (WiFi) |
| **`droid-mcp-bluetooth`** | `get_bluetooth_status` `list_paired_devices` | `BLUETOOTH` `BLUETOOTH_CONNECT` (API 31+) |
| **`droid-mcp-wifi`** | `get_wifi_info` `list_saved_networks` | `ACCESS_WIFI_STATE` `ACCESS_FINE_LOCATION` |
| **`droid-mcp-downloads`** | `list_downloads` `search_downloads` | `READ_EXTERNAL_STORAGE` (API < 33) / None (API 33+) |
| **`droid-mcp-screen`** | `get_screen_state` `get_display_info` | None |
| **`droid-mcp-tts`** | `speak_text` `get_tts_info` | None |
| **`droid-mcp-web`** | `web_search` `fetch_webpage` | `INTERNET` |
| **`droid-mcp-flashlight`** | `toggle_flashlight` `set_flashlight_brightness` | `CAMERA` `FLASHLIGHT` |
| **`droid-mcp-all`** | All of the above | All of the above |

---

## Tool Reference

### Device

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_device_info` | Model, manufacturer, OS version, screen dimensions | — |
| `get_battery_info` | Battery level, charging status, power source | — |
| `get_connectivity` | WiFi, cellular, Bluetooth connection status | — |
| `get_storage_info` | Total, available, and used storage | — |

### Calendar

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_calendar` | Read events for a date range | `start_date` (required), `end_date`, `limit` |
| `create_event` | Create a calendar event | `title` (required), `start` (required), `end` (required), `location`, `description`, `calendar_id` |
| `search_events` | Search events by keyword | `query` (required), `limit` |

### Contacts

| Tool | Description | Parameters |
|------|-------------|------------|
| `search_contacts` | Find contacts by name | `query` (required), `limit` |
| `read_contact` | Full details for a contact | `contact_id` (required) |
| `list_contacts` | Paginated contact list | `limit`, `offset` |

### SMS

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_messages` | Read SMS with filters | `box`, `address`, `since`, `limit` |
| `send_message` | Send an SMS (validated) | `to` (required), `body` (required) |
| `search_messages` | Search messages by keyword | `query` (required), `limit` |

### Files

| Tool | Description | Parameters |
|------|-------------|------------|
| `browse_files` | List directory contents | `path`, `limit` |
| `read_file` | Read text file content | `path` (required), `max_lines` |
| `search_files` | Recursive filename search | `query` (required), `path`, `limit` |

File access is sandboxed to external storage directories. Paths outside the allowed roots are rejected.

### Notifications

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_active_notifications` | Read active status bar notifications | `limit` |

Returns notifications posted by the host app by default. Full cross-app access requires configuring a `NotificationListenerService`.

### Call Log

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_call_log` | Recent call history | `limit`, `type` (all/incoming/outgoing/missed) |
| `search_call_log` | Search by number or name | `query` (required), `limit` |

### Media

| Tool | Description | Parameters |
|------|-------------|------------|
| `search_media` | Search photos and videos | `query`, `start_date`, `end_date`, `media_type`, `limit`, `offset` |
| `get_media_metadata` | Metadata for a media file | `media_id` (required) |
| `list_albums` | Photo/video albums with counts | `limit` |

### Location

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_current_location` | Device GPS coordinates | `accuracy` (fine/coarse) |
| `get_location_address` | Reverse geocode to address | `latitude` (required), `longitude` (required) |

### Health

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_step_count` | Steps since device reboot | — |
| `get_activity_info` | Available motion sensors | — |

Step data is sensor-based (not Health Connect). Values reset on device reboot.

### Clipboard

| Tool | Description | Parameters |
|------|-------------|------------|
| `read_clipboard` | Read current clipboard content | — |
| `write_clipboard` | Write text to clipboard | `text` (required), `label` |

### Apps

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_installed_apps` | List installed applications | `include_system`, `limit` |
| `get_app_info` | Details for a specific app | `package_name` (required) |
| `launch_app` | Launch an application | `package_name` (required) |

### Alarms

| Tool | Description | Parameters |
|------|-------------|------------|
| `create_alarm` | Set an alarm | `hour` (required), `minute` (required), `message`, `days` |
| `create_timer` | Start a countdown timer | `seconds` (required), `message` |
| `create_reminder` | Create a calendar reminder | `title` (required), `datetime` (required), `minutes_before` |

### Settings

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_settings` | Read brightness, volume, WiFi, Bluetooth status | — |
| `set_brightness` | Set screen brightness | `level` (required, 0-255) |
| `set_volume` | Set volume level | `stream` (media/ring/alarm), `level` (required) |
| `toggle_wifi` | Toggle WiFi on/off | `enabled` (required) |

On API 29+, `toggle_wifi` opens the system WiFi settings panel instead of toggling directly.

### Bluetooth

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_bluetooth_status` | Adapter status and name | — |
| `list_paired_devices` | Bonded Bluetooth devices | — |

### WiFi

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_wifi_info` | Current WiFi connection details | — |
| `list_saved_networks` | Saved WiFi networks | — |

SSID access requires location permission on API 26+. `list_saved_networks` returns empty on API 29+ due to platform restrictions.

### Downloads

| Tool | Description | Parameters |
|------|-------------|------------|
| `list_downloads` | Files in Downloads folder | `limit`, `sort_by` (date/name/size) |
| `search_downloads` | Search downloads by filename | `query` (required), `limit` |

### Screen

| Tool | Description | Parameters |
|------|-------------|------------|
| `get_screen_state` | Screen on/off, rotation, locked status | — |
| `get_display_info` | Resolution, density, refresh rate | — |

### Text-to-Speech

| Tool | Description | Parameters |
|------|-------------|------------|
| `speak_text` | Speak text aloud | `text` (required), `language`, `pitch`, `speed` |
| `get_tts_info` | Available TTS engines and languages | — |

### Web

| Tool | Description | Parameters |
|------|-------------|------------|
| `web_search` | Search the web via DuckDuckGo | `query` (required), `limit` |
| `fetch_webpage` | Fetch and extract text from a URL | `url` (required), `max_length` |

Web tools require internet connectivity. Search results include title, URL, and snippet. Webpage fetching strips HTML and returns clean text content.

### Flashlight

| Tool | Description | Parameters |
|------|-------------|------------|
| `toggle_flashlight` | Toggle flashlight on/off | `enabled` (required) |
| `set_flashlight_brightness` | Set flashlight brightness level (Android 13+) | `level` (required, 0-255) |

Flashlight tools require a device with camera flash hardware. `set_flashlight_brightness` is only available on Android 13+ (API 33+) and uses `turnOnTorchWithStrengthLevel()`.

---

## Desktop Connection

The HTTP transport allows any MCP client on your local network to connect to the phone.

**On the Android device:**

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .addTools(CalendarTools.all(context))
    .enableHttpServer(port = 8080, token = "your-secret-token")
    .build()

mcp.startServer()
```

**In Claude Code** (`~/.claude/settings.json`):

```json
{
  "mcpServers": {
    "droid-mcp": {
      "type": "http",
      "url": "http://<phone-ip>:8080/mcp",
      "headers": {
        "Authorization": "Bearer your-secret-token"
      }
    }
  }
}
```

The server exposes a health check at `GET /health` that returns the number of registered tools.

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│                 Android Application               │
│                                                    │
│  ┌──────────┐   ┌─────────────────────────────┐  │
│  │ Local LLM│◄─►│          DroidMcp            │  │
│  └──────────┘   │  ┌───────────┐ ┌──────────┐ │  │
│                  │  │ InProcess │ │   HTTP   │ │  │
│                  │  │ Transport │ │ Transport│ │  │
│                  │  └─────┬─────┘ └────┬─────┘ │  │
│                  │        └──────┬─────┘       │  │
│                  │         ToolRegistry         │  │
│                  └──────────┬──────────────────┘  │
│                             │                      │
│  ┌────────┬────────┬────────┼────────┬──────────┐ │
│  │ Device │Calendar│Contacts│  SMS   │  Files   │ │
│  │  Tools │ Tools  │ Tools  │ Tools  │  Tools   │ │
│  └───┬────┴───┬────┴───┬────┴───┬────┴────┬─────┘ │
│      └────────┴────────┴────────┴─────────┘       │
│                  Android APIs                      │
└──────────────────────────────────────────────────┘
```

The `InProcessTransport` provides direct function-call access for LLMs running on the device. The `HttpTransport` starts a Ktor server that implements the MCP protocol over HTTP, allowing desktop clients to connect over WiFi.

---

## Built-in Safety

| Feature | Description |
|---------|-------------|
| **Path sandboxing** | File operations are restricted to external storage. Attempts to access system paths or other app data are rejected. |
| **Input validation** | All parameters are validated. Numeric inputs are clamped to safe ranges. Phone numbers are checked against a format pattern before SMS is sent. |
| **Permission isolation** | Each module declares only its own permissions. The library never triggers permission requests — your app retains full control. |
| **Network security** | The HTTP server operates on the local network only. Optional Bearer token authentication is supported. |
| **No data collection** | The library contains no analytics, telemetry, crash reporting, or network calls to external services. |

---

## Requirements

| Requirement | Minimum |
|-------------|---------|
| Android | 9 (API 28) |
| Kotlin | 2.0+ |
| Gradle | 8.12+ |

---

## Project Structure

```
droid-mcp/
├── droid-mcp-core/            MCP protocol, transports, tool interface
├── droid-mcp-device/          Battery, connectivity, storage, device info
├── droid-mcp-calendar/        Calendar events
├── droid-mcp-contacts/        Contacts
├── droid-mcp-sms/             SMS messages
├── droid-mcp-files/           File browsing, reading, searching
├── droid-mcp-notifications/   Notifications
├── droid-mcp-calllog/         Call history
├── droid-mcp-media/           Photos, videos, albums
├── droid-mcp-location/        GPS location, geocoding
├── droid-mcp-health/          Step counter, motion sensors
├── droid-mcp-clipboard/       Clipboard read/write
├── droid-mcp-apps/            Installed apps, launch
├── droid-mcp-alarms/          Alarms, timers, reminders
├── droid-mcp-settings/        Brightness, volume, WiFi
├── droid-mcp-bluetooth/       Bluetooth status, paired devices
├── droid-mcp-wifi/            WiFi connection info
├── droid-mcp-downloads/       Downloads folder
├── droid-mcp-screen/          Screen state, display info
├── droid-mcp-tts/             Text-to-speech
├── droid-mcp-web/             Web search and page fetching
├── droid-mcp-flashlight/      Flashlight toggle and brightness
├── droid-mcp-all/             All modules combined
└── sample-app/                Demo application
```

---

## Contributing

Contributions are welcome. Please open an issue before submitting a pull request for non-trivial changes.

## License

```
Copyright 2026

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
