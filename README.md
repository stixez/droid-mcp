<h1 align="center">droid-mcp</h1>

<p align="center">
  <b>Give your AI app the whole phone.</b><br/>
  151 Android capabilities (calendar, messages, camera, sensors, UI automation, and more) as typed tools
  for on-device LLMs and any <a href="https://modelcontextprotocol.io">MCP</a> client.
</p>

<p align="center">
  <a href="https://github.com/stixez/droid-mcp/actions/workflows/ci.yml"><img src="https://github.com/stixez/droid-mcp/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <a href="https://jitpack.io/#stixez/droid-mcp"><img src="https://jitpack.io/v/stixez/droid-mcp.svg" alt="JitPack" /></a>
  <a href="https://stixez.github.io/droid-mcp/"><img src="https://img.shields.io/badge/docs-API%20reference-blue" alt="API docs" /></a>
  <img src="https://img.shields.io/badge/min%20SDK-28-blue" alt="Min SDK 28" />
  <img src="https://img.shields.io/badge/license-Apache%202.0-orange" alt="License" />
  <a href="https://buymeacoffee.com/stixe"><img src="https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?logo=buymeacoffee&logoColor=000" alt="Buy Me a Coffee" /></a>
</p>

---

droid-mcp is an Android SDK that turns phone capabilities into tools an LLM can call. Use them in two ways:

- **On-device**: call tools straight from your app's model output. No server involved.
- **Over MCP**: run a small HTTP server on the phone and connect Claude Code, Cursor, or any other MCP client over Wi-Fi.

Highlights:

- **Pick only what you need.** 53 independent modules. Each adds only its own permissions to your manifest.
- **Safe by default.** Bearer auth, Origin and session checks, an SSRF guard, sandboxed file access, read-only mode, per-tool gating, and optional user confirmation for risky calls.
- **Current MCP.** Protocol 2025-11-25, structured results, image content, progress, elicitation, and cancellation.
- **Your permission UX.** The library never asks for permissions. Your app decides when and how.

## Quick start

Add JitPack and the modules you want:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven { url = uri("https://jitpack.io") } }
}

// build.gradle.kts
dependencies {
    implementation("com.github.stixez.droid-mcp:droid-mcp-core:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-calendar:0.11.0")
    implementation("com.github.stixez.droid-mcp:droid-mcp-contacts:0.11.0")
    // or everything except the opt-in modules:
    // implementation("com.github.stixez.droid-mcp:droid-mcp-all:0.11.0")
}
```

Build it and call a tool:

```kotlin
val mcp = DroidMcp.builder()
    .addTools(CalendarTools.all(context))
    .addTools(ContactsTools.all(context))
    .build()

val result = mcp.callTool("read_calendar", mapOf("start_date" to "2026-10-01"))
if (result.isSuccess) println(result.data)   // {events=[...], count=3}
else println(result.errorMessage)
```

<details>
<summary>Packaging error about duplicate <code>META-INF</code> files?</summary>

Netty and BouncyCastle ship the same license files in several jars. Exclude them:

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
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF", // droid-mcp-tls only
            )
        }
    }
}
```

</details>

## Use it with an on-device model

Give the model the tool catalogue, then run whatever it picks:

```kotlin
val toolsJson = mcp.listToolsJson()           // names, descriptions, JSON Schemas

val result = mcp.callTool("send_message", mapOf("to" to "+15551234567", "body" to "On my way"))
```

Tool calls run on `Dispatchers.IO` with a timeout (5 minutes by default, `Builder.toolTimeout()`), so calling from the main thread is fine. Long tools can report progress: `mcp.callTool(name, params) { update -> … }`.

## Use it from a desktop MCP client

Start the server on the phone:

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .addTools(CalendarTools.all(context))
    .enableHttpServer(context = context)      // port 8080, token auto-generated
    .build()

mcp.startServer()
val token = mcp.serverToken                   // show it, or put it in a pairing QR
```

Then add the phone to your client, for example Claude Code (`~/.claude/settings.json`):

```json
{
  "mcpServers": {
    "phone": {
      "type": "http",
      "url": "http://<phone-ip>:8080/mcp",
      "headers": { "Authorization": "Bearer <token>" }
    }
  }
}
```

The phone advertises itself over mDNS (`_mcp._tcp`). Useful `enableHttpServer` options:
- `readOnly = true`: only tools that change nothing.
- `allowedOrigins`: browser-based clients such as MCP Inspector.
- `Builder.enableTls(...)`: HTTPS with a pinned self-signed certificate.

Pairing, sessions and TLS are covered in [docs/PAIRING.md](docs/PAIRING.md).

### Confirm risky calls

Ask the user before a destructive tool runs, on either transport:

```kotlin
DroidMcp.builder()
    .addTools(SmsTools.all(context))
    .confirmToolCalls { request ->            // every destructiveHint tool, by default
        withContext(Dispatchers.Main) { askUser(request.toolName, request.arguments) }
    }
```

If the user declines or doesn't answer in time, the result is `tool_call_declined` and the tool never runs. `ToolCallConfirmer.viaClientElicitation()` asks the desktop client's user instead.

## Modules

Every module depends only on `core`. The full list of tools, parameters and permissions is in **[docs/TOOLS.md](docs/TOOLS.md)**.

| Area | Modules |
|------|---------|
| Personal data | `calendar` `contacts` `sms` `calllog` `media` `files` `downloads` `clipboard` |
| Device | `device` `screen` `settings` `sensors` `health` `biometric` `keyguard` `flashlight` `vibration` `audio` `usb` `wallpaper` `ringtone` `dnd` |
| Connectivity | `network` `wifi` `bluetooth` `telephony` `nfc` `location` `web` |
| Camera & vision | `camera` `screenshot` `qr` `mlkit` |
| Apps & intents | `apps` `alarms` `intent` `print` `tts` `playback` |
| Notifications | `notifications` `notifications-reply` `notification-watch` |
| UI automation | `accessibility` `ime` `overlay` |
| Shell / root admin *(opt-in)* | `shizuku` `root`: install and uninstall apps, grant permissions, write settings, run allowlisted commands. See [docs/SHELL.md](docs/SHELL.md). |
| Hardening *(opt-in)* | `audit` (Room-backed call log), `tls` (self-signed HTTPS), `server-service` (foreground service that keeps the server alive) |

`droid-mcp-all` bundles everything except the opt-in modules, which pull extra dependencies (Shizuku, libsu, Room, BouncyCastle).

### Permissions

Your app requests runtime permissions itself. Check each provider first:

```kotlin
if (CalendarTools.hasPermissions(context)) builder.addTools(CalendarTools.all(context))
else requestPermissions(CalendarTools.requiredPermissions())
```

Some providers only register tools whose permission is granted, for example `create_event` needs `WRITE_CALENDAR`, so rebuild after a grant. A few modules need a grant in system Settings instead:

| Module | Needs | Granted in |
|--------|-------|-----------|
| `playback`, `notifications-reply`, `notification-watch` | Notification access. Your service extends `McpNotificationListenerServiceBase`. | Settings › Apps › Special access › Notification access |
| `accessibility` | Your service extends `DroidMcpAccessibilityService` | Settings › Accessibility |
| `ime` | Your service extends `DroidMcpInputMethodService`, enabled and selected | Settings › System › Keyboard |
| `overlay` | Display over other apps | Settings › Apps › Special access |
| `screenshot` | MediaProjection consent, handed to `MediaProjectionHolder` from a `mediaProjection` foreground service | Consent dialog |
| `dnd` | Do Not Disturb access (`set_dnd_mode`) | Settings › Apps › Special access |
| `settings`, `ringtone` | Modify system settings (`set_brightness`, `set_ringtone`) | Settings › Apps › Special access |
| `network` | Usage access (`get_data_usage`) | Settings › Apps › Special access |
| `shizuku`, `root` | Shizuku running, or a rooted device | See [docs/SHELL.md](docs/SHELL.md) |

> [!NOTE]
> Android 10+ blocks activity launches from the background. Tools that open another app (`launch_app`, alarms, intents, `toggle_wifi`) need your app in the foreground or holding the overlay permission. Otherwise they return an error.

## Security

The HTTP server:
- requires a bearer token
- rejects foreign `Origin` headers, non-JSON bodies, bodies over 4 MB and unknown protocol versions
- ties every request to a session

Tools are also constrained: file tools only read external storage, web tools refuse private and loopback addresses, and intents use an action and URI-scheme allowlist. The server listens on every network the phone joins, so use it on networks you trust or enable TLS. The threat model is in [docs/SECURITY.md](docs/SECURITY.md).

droid-mcp itself sends no analytics. Google ML Kit, used by `mlkit` and `qr`, reports usage to Google under its own terms.

## Requirements

- Android 9 (API 28) or newer at runtime
- `compileSdk` 36+, AGP 8.9.1+ and Kotlin 2.3+ in your app

## Sample app

`sample-app` registers every module and has four tabs:
- **Tools**: a test button per tool, plus shortcuts to grant special permissions.
- **Gating**: switch tools on and off on the live server.
- **Activity**: recent calls.
- **Audit**: the persisted call log, with JSON export.

Start the server there and pair a desktop client with the QR code.

## Documentation

| | |
|---|---|
| [Tools](docs/TOOLS.md) | Every tool, its parameters and permissions |
| [Pairing](docs/PAIRING.md) | Connecting clients: tokens, sessions, mDNS, QR, TLS |
| [Security](docs/SECURITY.md) | Threat model and trust boundaries |
| [Shell tools](docs/SHELL.md) | Shizuku and root setup, safety policy |
| [Versioning](docs/VERSIONING.md) | Compatibility promise and how CI enforces it |
| [API reference](https://stixez.github.io/droid-mcp/) | KDoc for every module |
| [Releases](https://github.com/stixez/droid-mcp/releases) | Release notes |

## Contributing

Contributions are welcome. For anything non-trivial, please open an issue first.

## License

Apache License 2.0. See [LICENSE](LICENSE).
