# droid-mcp

Privacy-first MCP SDK for Android. Gives local LLMs structured access to your phone data — calendar, contacts, SMS, device info. Everything stays on device.

## What is this?

An Android library that exposes phone capabilities via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io). Built for the era of on-device AI (Gemma 4, Llama, etc.) where your AI assistant runs locally on your phone.

**No cloud. No API keys. No data leaves your phone.**

## Quick Start

```kotlin
// 1. Add dependencies
implementation("io.droidmcp:core:0.1.0")
implementation("io.droidmcp:calendar:0.1.0")
implementation("io.droidmcp:contacts:0.1.0")

// 2. Initialize
val mcp = DroidMcp.builder()
    .addTools(CalendarTools.all(context))
    .addTools(ContactsTools.all(context))
    .addTools(DeviceTools.all(context))
    .build()

// 3. Use from your local LLM
val tools = mcp.listToolsJson()        // tool definitions for LLM prompt
val result = mcp.callTool("read_calendar", mapOf("start_date" to "2026-04-10"))
```

## Modules

Pick only what you need:

| Module | Tools | Permissions |
|---|---|---|
| `droid-mcp-core` | MCP protocol, transports | INTERNET (for HTTP server) |
| `droid-mcp-device` | Battery, connectivity, storage, device info | None |
| `droid-mcp-calendar` | Read/create/search calendar events | READ_CALENDAR, WRITE_CALENDAR |
| `droid-mcp-contacts` | Search/read/list contacts | READ_CONTACTS |
| `droid-mcp-sms` | Read/send/search SMS | READ_SMS, SEND_SMS |
| `droid-mcp-all` | All of the above | All of the above |

## Desktop Connection

Connect Claude Code (or any MCP client) to your phone over WiFi:

```kotlin
val mcp = DroidMcp.builder()
    .addTools(DeviceTools.all(context))
    .enableHttpServer(port = 8080)
    .build()

mcp.startServer()
```

In Claude Code config:
```json
{
  "droid-mcp": {
    "type": "http",
    "url": "http://<phone-ip>:8080/mcp"
  }
}
```

## Requirements

- Android 9+ (API 28)
- Kotlin 2.0+

## License

Apache 2.0
