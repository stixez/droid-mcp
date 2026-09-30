# Pairing a desktop MCP client

A desktop client can find and connect to the phone's HTTP server in two ways:

1. **mDNS.** The server advertises itself as `_mcp._tcp` on the local network. mDNS is only used when `enableHttpServer(context = ...)` receives an Android `Context`.
2. **QR payload.** The sample app shows a QR code with everything a client needs. The payload format is documented below so clients can import it.

## Connecting

The server speaks MCP Streamable HTTP at `/mcp`.

- `POST /mcp` carries JSON-RPC. The body must be `Content-Type: application/json` and at most 4 MB.
- **Auth.** Auth is on by default (`requireAuth = true`). Send `Authorization: Bearer <token>`: the `Bearer` scheme is required, and its case doesn't matter. A missing or wrong token gets `401` with `WWW-Authenticate: Bearer realm="droid-mcp"`.
- **Sessions.** A successful `initialize` response carries an `Mcp-Session-Id` header. Send it on every later request. A missing session ID gets `400`. An unknown one, or one issued to a different token, gets `404`.
- **Protocol version.** An `MCP-Protocol-Version` header the server doesn't support gets `400`. Supported versions: 2025-11-25, 2025-06-18, 2025-03-26 and 2024-11-05.
- **Origin.** A request with an `Origin` header not listed in `enableHttpServer(allowedOrigins = ...)` gets `403`. Native clients send no `Origin` and are not affected. Browser tools such as MCP Inspector must be allowlisted.
- `DELETE /mcp` with the session header ends the session.
- `GET /mcp` returns `405`, because the server never pushes messages. There is no SSE stream.
- `GET /health` returns tool count and read-only state. It needs the same bearer token.

## mDNS service

| Field | Value |
|---|---|
| Service type | `_mcp._tcp.` |
| Service name | `droid-mcp-<Build.MODEL>`: characters other than `A-Z a-z 0-9 -` become `-`, and the name is capped at 63 characters |
| Port | The bound port: `TlsConfig.httpsPort` when TLS is on, otherwise `enableHttpServer(port = ...)` |

TXT records:

| Key | Value | Meaning |
|---|---|---|
| `version` | e.g. `0.11.0` | droid-mcp version on the device |
| `auth` | `bearer` \| `none` | Whether a bearer token is required |
| `readonly` | `true` \| `false` | Whether the server exposes only read-only tools |
| `tls` | `true` \| `false` | Whether the endpoint is HTTPS |

The token is never broadcast.

Check from macOS:

```bash
dns-sd -B _mcp._tcp.
# Expect an instance such as "droid-mcp-Pixel-8"
```

## QR pairing payload

The sample app shows the QR code while the server runs. It encodes one JSON object:

```json
{
  "v": 1,
  "url": "http://192.168.1.42:8080/mcp",
  "token": "8t3wQ...",
  "tls_fingerprint": "AB:CD:EF:...",
  "name": "Pixel 8"
}
```

| Field | Required | Description |
|---|---|---|
| `v` | yes | Schema version, currently `1`. Clients should reject unknown versions. |
| `url` | yes | Full endpoint URL. It starts with `https://` when TLS is on. |
| `token` | no | Bearer token (`DroidMcp.serverToken`). Left out when there is no auth. |
| `tls_fingerprint` | no | Only present when TLS is on. See [TLS](#tls). |
| `name` | yes | Display name (`Build.MODEL` in the sample) |

To connect by hand, copy `url` and `token` into your client's configuration. For Claude Code:

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

## Tokens

- **Generated token.** Without an explicit token, the server generates 32 random bytes with `SecureRandom`, encoded as URL-safe base64 without padding. The token is created when `build()` runs. It stays the same across `stopServer()` / `startServer()` on that `DroidMcp` instance. A newly built instance gets a new token.
- **Custom token.** `enableHttpServer(token = ...)` sets a fixed token. It must be at least 16 characters, or the call throws `IllegalArgumentException`.
- **Rotation.** `DroidMcp.rotateToken()` replaces the primary token and returns the new one. The old one stops working. Show a new QR code afterwards.
- **Per-client tokens.** `DroidMcp.pairClient(label)` issues a separate token for one client, and `revokeClient(label)` revokes it. Rotating the primary token doesn't affect these. `pairedClients()` lists them.

## TLS

`enableTls(config)` serves HTTPS on `TlsConfig.httpsPort` (default 8443) instead of the plaintext port. The opt-in `droid-mcp-tls` module creates a self-signed certificate and saves it:

```kotlin
val tls = SelfSignedCert.loadOrCreate(File(context.filesDir, "droid-mcp-tls.p12"))
DroidMcp.builder()
    .enableHttpServer(context = context)
    .enableTls(tls)
```

- The certificate is self-signed (`CN=droid-mcp`, no SANs), so clients can't validate it through a CA chain. Instead they should pin the SHA-256 fingerprint: `DroidMcp.tlsFingerprint`, which is the `tls_fingerprint` field in the QR code.
- The fingerprint is colon-separated uppercase hex of the certificate's DER encoding.
- Reusing the same keystore file keeps the fingerprint the same across restarts.

## Read-only mode

`enableHttpServer(readOnly = true)` lists only tools annotated `readOnlyHint`. Calls to any other tool return an MCP content error (`isError: true`, `"Tool '<name>' is not available in read-only mode"`).

On an untrusted network, keep `requireAuth = true`, since mDNS makes the device discoverable.
