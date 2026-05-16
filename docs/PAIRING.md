# Pairing droid-mcp with an MCP client

Starting with **0.4.0**, the HTTP server has two discovery mechanisms aimed at making it painless to connect a desktop MCP client to your phone:

1. **mDNS broadcast** — the server publishes itself on the local network as `_mcp._tcp`. Clients on the same Wi-Fi can find the device without knowing its IP.
2. **QR pairing payload** — the sample app renders a QR that encodes everything a client needs to connect.

Both are opt-in. mDNS only activates when the Builder is given an Android `Context`. The QR is rendered by the sample app, but the payload format is documented here so other MCP clients can implement importers.

## mDNS service

| Field          | Value                                |
|----------------|--------------------------------------|
| Service type   | `_mcp._tcp.`                         |
| Service name   | `droid-mcp-<device-model>`           |
| Port           | Whatever you passed to `enableHttpServer(port = ...)` |

### TXT records

| Key       | Value                              | Meaning                                     |
|-----------|------------------------------------|---------------------------------------------|
| `version` | e.g. `0.4.0`                       | droid-mcp version running on the device     |
| `auth`    | `bearer` \| `none`                 | Whether the `/mcp` endpoint requires a token |
| `readonly`| `true` \| `false`                  | Whether destructive tools are gated         |

A client that reads the TXT record can decide whether to prompt for a token before connecting, and whether to filter its tool list for read-only mode.

### Verifying from macOS

```bash
dns-sd -B _mcp._tcp.
# Expect a line containing "droid-mcp-Pixel-8" (or your device model)
```

## QR pairing payload

The sample app displays a QR code when the server is running. The QR encodes a single JSON object:

```json
{
  "v": 1,
  "url": "http://192.168.1.42:8080/mcp",
  "token": "8t3wQ...base64url...",
  "name": "Pixel 8"
}
```

| Field   | Required | Description                                  |
|---------|----------|----------------------------------------------|
| `v`     | yes      | Schema version (currently `1`)               |
| `url`   | yes      | Full URL of the MCP endpoint                 |
| `token` | no       | Bearer token. Omitted if `requireAuth=false` |
| `name`  | yes      | Human-readable device name                   |

### Client behavior

When a client imports a pairing QR it should:

1. Parse the JSON. If `v` is unknown, fail.
2. Configure the MCP server entry with `url` and `token` (if present).
3. Use `name` as the display label.

Today, MCP clients do not autoscan from a QR — paste the URL + token into config manually. The QR is the source of truth for both fields.

## Server config in Claude Code

Until clients add QR import, paste the values into your config. For Claude Code:

```json
{
  "mcpServers": {
    "droid": {
      "type": "http",
      "url": "http://192.168.1.42:8080/mcp",
      "headers": {
        "Authorization": "Bearer 8t3wQ..."
      }
    }
  }
}
```

## Security notes

- The bearer token is generated locally on the phone with `SecureRandom` and is 32 bytes encoded as URL-safe base64. It is unique to each server start unless you pass an explicit `token` to `enableHttpServer`.
- The token is **not** broadcast via mDNS — only the existence of the service is. The token must reach the client out-of-band (QR scan, copy/paste, etc.).
- mDNS makes the device discoverable on the local network. If you are on an untrusted network, prefer `requireAuth = true` (the default).
- Use `readOnly = true` if you only want the client to see read-only tools — the server will respond to `tools/call` for destructive tools with an MCP content error (`isError: true`, message `"Tool '<name>' is not available in read-only mode"`).
