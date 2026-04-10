# droid-mcp

**Privacy-first MCP SDK for Android.** Gives local LLMs structured access to your phone data — calendar, contacts, SMS, files, photos, location, and more. Everything stays on device.

No cloud. No API keys. No data leaves your phone.

---

## What is this?

An open-source Android library that exposes phone capabilities via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). Built for the era of on-device AI — Gemma 4, Llama, and other local LLMs that run directly on your phone.

Instead of screen-tapping or sending your data to the cloud, droid-mcp gives AI **direct, typed access** to Android APIs: read your calendar, search contacts, browse files, check your location — all through a standard protocol, all on-device.

### Why droid-mcp?

- **Privacy first** — Your data never leaves your phone. No servers, no cloud, no tracking.
- **MCP standard** — Works with any MCP-compatible client (Claude Code, Cursor, custom apps).
- **Modular** — Only include what you need. Calendar but not SMS? Just add that one module.
- **Production ready** — Input validation, path sandboxing, permission helpers, error handling.
- **Two transports** — In-process calls for on-device LLMs, HTTP server for desktop connections.

---

## Quick Start

### 1. Add dependencies

Pick only the modules you need:

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.droidmcp:core:0.1.0")
    implementation("io.droidmcp:calendar:0.1.0")
    implementation("io.droidmcp:contacts:0.1.0")
    implementation("io.droidmcp:device:0.1.0")
}

// Or include everything:
// implementation("io.droidmcp:all:0.1.0")
```

### 2. Initialize

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))         // always available, no permissions
    .addTools(CalendarTools.all(context))        // needs READ_CALENDAR
    .addTools(ContactsTools.all(context))        // needs READ_CONTACTS
    .build()
```

### 3. Use from your local LLM

```kotlin
// Get tool definitions (JSON) to include in your LLM prompt
val toolsJson = mcp.listToolsJson()

// Call a tool with parameters
val result = mcp.callTool("read_calendar", mapOf(
    "start_date" to "2026-04-10",
    "end_date" to "2026-04-17"
))

// Check the result
if (result.isSuccess) {
    val events = result.data  // Map with "events" list and "count"
} else {
    val error = result.errorMessage
}
```

### 4. Handle permissions

The library never requests permissions itself. Your app handles the UX:

```kotlin
// Check what's granted
if (CalendarTools.hasPermissions(context)) {
    mcp.addTools(CalendarTools.all(context))
}

// Get the list of needed permissions
val needed = CalendarTools.requiredPermissions()
// → [Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR]
```

---

## Modules

### Core

| Module | Description | Permissions |
|--------|-------------|-------------|
| `droid-mcp-core` | MCP protocol, tool interface, in-process + HTTP transports | INTERNET (for HTTP server only) |

### Tier 1 — Essential Phone Data

| Module | Tools | Permissions |
|--------|-------|-------------|
| `droid-mcp-device` | `get_device_info`, `get_battery_info`, `get_connectivity`, `get_storage_info` | None |
| `droid-mcp-calendar` | `read_calendar`, `create_event`, `search_events` | READ_CALENDAR, WRITE_CALENDAR |
| `droid-mcp-contacts` | `search_contacts`, `read_contact`, `list_contacts` | READ_CONTACTS |
| `droid-mcp-sms` | `read_messages`, `send_message`, `search_messages` | READ_SMS, SEND_SMS |

### Tier 2 — Files, Notifications, Calls

| Module | Tools | Permissions |
|--------|-------|-------------|
| `droid-mcp-files` | `browse_files`, `read_file`, `search_files` | READ_EXTERNAL_STORAGE (API < 33) / READ_MEDIA_* (API 33+) |
| `droid-mcp-notifications` | `get_active_notifications` | None (own-app only; full access needs NotificationListenerService) |
| `droid-mcp-calllog` | `read_call_log`, `search_call_log` | READ_CALL_LOG |

### Tier 3 — Media, Location, Health

| Module | Tools | Permissions |
|--------|-------|-------------|
| `droid-mcp-media` | `search_media`, `get_media_metadata`, `list_albums` | READ_EXTERNAL_STORAGE (API < 33) / READ_MEDIA_IMAGES, READ_MEDIA_VIDEO (API 33+) |
| `droid-mcp-location` | `get_current_location`, `get_location_address` | ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION |
| `droid-mcp-health` | `get_step_count`, `get_activity_info` | ACTIVITY_RECOGNITION (API 29+) |

### Convenience

| Module | Description |
|--------|-------------|
| `droid-mcp-all` | Pulls in all modules above. One dependency for everything. |

---

## Tool Reference

### Device Tools

**`get_device_info`** — Device model, manufacturer, OS version, screen dimensions.
```json
// No parameters required
// Returns: manufacturer, model, brand, os_version, sdk_version, screen_width, screen_height, screen_density
```

**`get_battery_info`** — Battery level and charging status.
```json
// Returns: level_percent, is_charging, charging_source (usb/ac/wireless/none)
```

**`get_connectivity`** — Network status.
```json
// Returns: is_connected, has_wifi, has_cellular, has_bluetooth
```

**`get_storage_info`** — Available storage space.
```json
// Returns: total_bytes, available_bytes, used_bytes, total_gb, available_gb
```

### Calendar Tools

**`read_calendar`** — Read events for a date range.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `start_date` | string | yes | YYYY-MM-DD format |
| `end_date` | string | no | Defaults to start_date |
| `limit` | int | no | Max results (1-100, default 20) |

**`create_event`** — Create a calendar event.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | yes | Event title |
| `start` | string | yes | YYYY-MM-DD HH:mm |
| `end` | string | yes | YYYY-MM-DD HH:mm |
| `location` | string | no | Event location |
| `description` | string | no | Event description |
| `calendar_id` | int | no | Defaults to primary calendar |

**`search_events`** — Search events by keyword in title or description.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | yes | Search keyword |
| `limit` | int | no | Max results (1-100, default 20) |

### Contacts Tools

**`search_contacts`** — Find contacts by name.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | yes | Name to search |
| `limit` | int | no | Max results (1-100, default 20) |

**`read_contact`** — Full details for a specific contact.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `contact_id` | int | yes | Contact ID |

**`list_contacts`** — Paginated list of all contacts.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `limit` | int | no | Per page (1-100, default 50) |
| `offset` | int | no | Skip count (default 0) |

### SMS Tools

**`read_messages`** — Read SMS messages with filters.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `box` | string | no | "inbox" or "sent" (default "inbox") |
| `address` | string | no | Filter by phone number |
| `since` | string | no | Messages after YYYY-MM-DD |
| `limit` | int | no | Max results (1-100, default 20) |

**`send_message`** — Send an SMS. Phone number is validated before sending.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `to` | string | yes | Recipient phone number |
| `body` | string | yes | Message text |

**`search_messages`** — Search SMS by keyword.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | yes | Search keyword |
| `limit` | int | no | Max results (1-100, default 20) |

### Files Tools

**`browse_files`** — List files in a directory. Sandboxed to external storage.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `path` | string | no | Directory path (default: /sdcard) |
| `limit` | int | no | Max results (1-100, default 50) |

**`read_file`** — Read text file content. Returns error for binary files.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `path` | string | yes | File path |
| `max_lines` | int | no | Lines to read (default 100) |

**`search_files`** — Recursive file name search. Max depth 5 directories.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | yes | Filename pattern |
| `path` | string | no | Starting directory (default: /sdcard) |
| `limit` | int | no | Max results (1-100, default 20) |

### Notifications Tools

**`get_active_notifications`** — Read active notifications. Note: only sees notifications from the host app unless NotificationListenerService is configured.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `limit` | int | no | Max results (1-100, default 20) |

### Call Log Tools

**`read_call_log`** — Recent call history.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `limit` | int | no | Max results (1-100, default 20) |
| `type` | string | no | "all", "incoming", "outgoing", "missed" (default "all") |

**`search_call_log`** — Search calls by number or contact name.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | yes | Phone number or name |
| `limit` | int | no | Max results (1-100, default 20) |

### Media Tools

**`search_media`** — Search photos and videos.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | no | Filename keyword |
| `start_date` | string | no | YYYY-MM-DD |
| `end_date` | string | no | YYYY-MM-DD |
| `media_type` | string | no | "images", "videos", "all" (default "all") |
| `limit` | int | no | Max results (1-100, default 20) |
| `offset` | int | no | Skip count (default 0) |

**`get_media_metadata`** — Detailed metadata for a media file.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `media_id` | int | yes | Media ID from search results |

**`list_albums`** — List photo/video albums with item counts.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `limit` | int | no | Max albums (1-100, default 50) |

### Location Tools

**`get_current_location`** — Device's current location (uses last known location).
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `accuracy` | string | no | "fine" or "coarse" (default "coarse") |

**`get_location_address`** — Reverse geocode coordinates to address.
| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `latitude` | number | yes | Latitude |
| `longitude` | number | yes | Longitude |

### Health Tools

**`get_step_count`** — Steps since last device reboot (sensor-based).
```json
// No parameters. Returns: steps, sensor_name, note about since-reboot limitation
```

**`get_activity_info`** — Available motion/health sensors on the device.
```json
// No parameters. Returns: has_step_counter, has_step_detector, sensors list, step_count (if available)
```

---

## Desktop Connection

Connect Claude Code (or any MCP client) to your phone over WiFi:

```kotlin
// In your Android app
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .addTools(CalendarTools.all(context))
    .enableHttpServer(port = 8080, token = "my-secret-token")  // optional auth
    .build()

mcp.startServer()
```

In Claude Code settings (`~/.claude/settings.json`):
```json
{
  "mcpServers": {
    "droid-mcp": {
      "type": "http",
      "url": "http://192.168.1.50:8080/mcp",
      "headers": {
        "Authorization": "Bearer my-secret-token"
      }
    }
  }
}
```

Then ask Claude: *"What's on my calendar this week?"* — it calls your phone's MCP server, reads your calendar locally, and responds.

### Health endpoint

Check if the server is running:
```bash
curl http://192.168.1.50:8080/health
# → {"status":"ok","tools":15}
```

---

## Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     Your Android App                        │
│                                                             │
│  ┌───────────┐    ┌──────────────────────────────────────┐ │
│  │ Local LLM │◄──►│            DroidMcp                  │ │
│  │ (Gemma 4) │    │  ┌──────────────┐ ┌──────────────┐  │ │
│  └───────────┘    │  │ InProcess    │ │ HTTP/SSE     │  │ │
│                   │  │ Transport    │ │ Transport    │  │ │
│                   │  └──────┬───────┘ └──────┬───────┘  │ │
│                   │         │                 │          │ │
│                   │  ┌──────┴─────────────────┴───────┐  │ │
│                   │  │        ToolRegistry             │  │ │
│                   │  └──┬────┬────┬────┬────┬────┬──┘  │ │
│                   └─────┼────┼────┼────┼────┼────┼─────┘ │
│                         │    │    │    │    │    │        │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ │
│  │Device│ │ Cal  │ │Contct│ │ SMS  │ │Files │ │Media │ │
│  │Tools │ │Tools │ │Tools │ │Tools │ │Tools │ │Tools │ │
│  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ │
│     │        │        │        │        │        │      │
│  ┌──┴────────┴────────┴────────┴────────┴────────┴───┐  │
│  │              Android System APIs                    │  │
│  │  BatteryManager, CalendarContract, ContactsContract │  │
│  │  Telephony.Sms, File API, MediaStore, LocationMgr  │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## Security

- **Path sandboxing** — File tools are restricted to external storage. No access to `/data`, `/system`, or other app directories.
- **Input validation** — All parameters are validated and clamped (limit 1-100, phone number format check, etc.).
- **Permission model** — The library never requests permissions. Your app controls what's granted.
- **SMS safety** — Phone numbers are validated before sending. No bulk operations.
- **HTTP auth** — Optional Bearer token authentication for the HTTP transport.
- **Local network only** — HTTP server binds to the device's local network, not exposed to the internet.

---

## Requirements

- Android 9+ (API 28)
- Kotlin 2.0+
- Gradle 8.12+

---

## Project Structure

```
droid-mcp/
├── droid-mcp-core/           # MCP protocol, transports, tool interface
├── droid-mcp-device/         # Battery, connectivity, storage, device info
├── droid-mcp-calendar/       # Calendar events
├── droid-mcp-contacts/       # Contacts
├── droid-mcp-sms/            # SMS messages
├── droid-mcp-files/          # File browsing, reading, searching
├── droid-mcp-notifications/  # Active notifications
├── droid-mcp-calllog/        # Call history
├── droid-mcp-media/          # Photos, videos, albums
├── droid-mcp-location/       # GPS location, geocoding
├── droid-mcp-health/         # Step counter, motion sensors
├── droid-mcp-all/            # Convenience: all modules
└── sample-app/               # Demo app
```

---

## License

Apache 2.0
