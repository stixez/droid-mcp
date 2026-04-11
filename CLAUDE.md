# droid-mcp

Android MCP SDK. Exposes phone capabilities (calendar, contacts, SMS, files, media, location, etc.) via Model Context Protocol — 48 tools across 21 modules, compatible with on-device LLMs and desktop MCP clients.

## Quick Reference

- **Language:** Kotlin 2.1, Android SDK 28+, Gradle 8.12
- **Build:** `./gradlew assembleDebug` | **Test:** `./gradlew :droid-mcp-core:test`
- **21 modules**, 48 tools, sample app with Compose UI

## Key Conventions

- Every tool implements `McpTool` interface: `name`, `description`, `parameters`, `suspend fun execute()`
- Every module has a provider object (e.g. `CalendarTools`) with `all(context)`, `requiredPermissions()`, `hasPermissions(context)`
- Limit params: `(params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10`
- ContentResolver queries: use `?.use { cursor -> }`, NO `LIMIT`/`OFFSET` in sortOrder (handle in cursor loop)
- File access: sandboxed via `PathValidator` — external storage only
- Tool calls run on `Dispatchers.IO`, never main thread
- No fully qualified names inline — use proper imports

## Don't

- Don't add `LIMIT` or `OFFSET` to ContentResolver `sortOrder` strings — not supported on all devices
- Don't request permissions from library code — apps handle their own permission UX
- Don't use `SmsManager.getDefault()` — use version-checked `context.getSystemService()` on API 31+
- Don't require media permissions for File API access on API 33+

<!-- SECTION: architecture -->

## Architecture

Multi-module Gradle project. Each phone API is an independent module depending only on `:droid-mcp-core`.

**Core** (`droid-mcp-core`):
- `McpTool` — interface every tool implements
- `ToolParameter`, `ToolResult` — typed params and results
- `ToolRegistry` — thread-safe tool collection (ConcurrentHashMap)
- `McpProtocolImpl` — JSON-RPC 2.0 handler (initialize, tools/list, tools/call)
- `InProcessTransport` — direct calls for on-device LLMs
- `HttpTransport` — Ktor server for desktop MCP clients
- `DroidMcp` — builder pattern entry point

**Tool modules** (each follows identical pattern):
```
droid-mcp-{name}/
  build.gradle.kts          — android library, depends on :droid-mcp-core
  src/main/AndroidManifest.xml — declares module-specific permissions
  src/main/kotlin/io/droidmcp/{name}/
    {Name}Tools.kt           — provider object
    {ToolName}Tool.kt         — individual tool implementations
```

**Convenience:** `droid-mcp-all` — `api()` dependency on every module
**Sample:** `sample-app` — Compose UI, Material 3, dynamic colors, all tools registered

<!-- SECTION: modules -->

## Modules

| Module | Package | Tools |
|--------|---------|-------|
| `droid-mcp-core` | `io.droidmcp.core` | Protocol, transports, interfaces |
| `droid-mcp-device` | `io.droidmcp.device` | get_device_info, get_battery_info, get_connectivity, get_storage_info |
| `droid-mcp-calendar` | `io.droidmcp.calendar` | read_calendar, create_event, search_events |
| `droid-mcp-contacts` | `io.droidmcp.contacts` | search_contacts, read_contact, list_contacts |
| `droid-mcp-sms` | `io.droidmcp.sms` | read_messages, send_message, search_messages |
| `droid-mcp-files` | `io.droidmcp.files` | browse_files, read_file, search_files |
| `droid-mcp-notifications` | `io.droidmcp.notifications` | get_active_notifications |
| `droid-mcp-calllog` | `io.droidmcp.calllog` | read_call_log, search_call_log |
| `droid-mcp-media` | `io.droidmcp.media` | search_media, get_media_metadata, list_albums |
| `droid-mcp-location` | `io.droidmcp.location` | get_current_location, get_location_address |
| `droid-mcp-health` | `io.droidmcp.health` | get_step_count, get_activity_info |
| `droid-mcp-clipboard` | `io.droidmcp.clipboard` | read_clipboard, write_clipboard |
| `droid-mcp-apps` | `io.droidmcp.apps` | list_installed_apps, get_app_info, launch_app |
| `droid-mcp-alarms` | `io.droidmcp.alarms` | create_alarm, create_timer, create_reminder |
| `droid-mcp-settings` | `io.droidmcp.settings` | get_settings, set_brightness, set_volume, toggle_wifi |
| `droid-mcp-bluetooth` | `io.droidmcp.bluetooth` | get_bluetooth_status, list_paired_devices |
| `droid-mcp-wifi` | `io.droidmcp.wifi` | get_wifi_info, list_saved_networks |
| `droid-mcp-downloads` | `io.droidmcp.downloads` | list_downloads, search_downloads |
| `droid-mcp-screen` | `io.droidmcp.screen` | get_screen_state, get_display_info |
| `droid-mcp-tts` | `io.droidmcp.tts` | speak_text, get_tts_info |
| `droid-mcp-web` | `io.droidmcp.web` | web_search, fetch_webpage |

<!-- SECTION: new-tool-guide -->

## Adding a New Tool Module

1. Create `droid-mcp-{name}/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "io.droidmcp.{name}"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
}
dependencies { implementation(project(":droid-mcp-core")) }
```

2. Create `src/main/AndroidManifest.xml` with required permissions

3. Implement tools extending `McpTool`:
```kotlin
class MyTool(private val context: Context) : McpTool {
    override val name = "my_tool"
    override val description = "What it does"
    override val parameters = listOf(
        ToolParameter("param", "Description", ParameterType.STRING, required = true),
    )
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val param = params["param"]?.toString() ?: return ToolResult.error("param is required")
        // ... do work ...
        return ToolResult.success(mapOf("result" to value))
    }
}
```

4. Create provider object:
```kotlin
object MyTools {
    fun all(context: Context): List<McpTool> = listOf(MyTool(context))
    fun requiredPermissions(): List<String> = listOf(Manifest.permission.WHATEVER)
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
```

5. Add to `settings.gradle.kts`, `droid-mcp-all/build.gradle.kts`, `sample-app` ViewModel, and README

<!-- SECTION: testing -->

## Testing

- **Unit tests** (core module): `./gradlew :droid-mcp-core:test` — 25 tests covering ToolRegistry, ToolParameter, ToolResult, McpProtocol, InProcessTransport, DroidMcp builder
- **Tool modules**: Android API-dependent, tested via sample app on device/emulator
- **Full build**: `./gradlew assembleDebug`
- **HTTP transport**: Start server in sample app, connect from Claude Code via `http://<phone-ip>:8080/mcp`

<!-- SECTION: security -->

## Security Decisions

- File tools sandboxed to `Environment.getExternalStorageDirectory()` via `PathValidator`
- SMS `send_message` validates phone number format before sending
- HTTP transport: local network only, optional Bearer token auth
- MCP protocol: malformed JSON returns -32700 parse error (no crash)
- All numeric params clamped to safe ranges
- `ToolRegistry` uses `ConcurrentHashMap` for thread safety
- Settings read tools register without write permission; write tools require `canWrite()`
