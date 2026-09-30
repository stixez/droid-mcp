# Pairing a desktop MCP client

This page describes how a desktop MCP client finds, authenticates to and talks with the phone's HTTP server.

A client can find the server in two ways:

- **mDNS.** The server advertises `_mcp._tcp` on the local network when `enableHttpServer(context = ...)` receives an Android `Context`.
- **QR code.** The sample app shows a QR code with everything a client needs. The [payload format](#qr-payload) is documented so clients can import it.

## Connecting

The server speaks MCP Streamable HTTP at `/mcp`, with protocol versions 2025-11-25, 2025-06-18, 2025-03-26 and 2024-11-05.

| Request | Behavior |
|---|---|
| `POST /mcp` | JSON-RPC. The body must be `Content-Type: application/json` (415 otherwise) and at most 4 MB (413 otherwise). |
| `DELETE /mcp` | Ends the session named in `Mcp-Session-Id`. 200 when it existed, 404 otherwise. |
| `GET /mcp` | 405. There is no standalone server stream. |
| `GET /health` | Tool count and read-only state. Needs the bearer token. |

Each request is checked for:

1. **Origin.** A request with an `Origin` header not listed in `enableHttpServer(allowedOrigins = ...)` gets 403. Native clients send no `Origin` and are unaffected; browser tools such as MCP Inspector must be allowlisted.
2. **Protocol version.** An `MCP-Protocol-Version` header the server doesn't support gets 400.
3. **Auth.** Auth is on by default (`requireAuth = true`). Send `Authorization: Bearer <token>`. The `Bearer` scheme is required and case-insensitive. A missing or wrong token gets 401 with `WWW-Authenticate: Bearer realm="droid-mcp"`.
4. **Session.** A successful `initialize` response carries an `Mcp-Session-Id` header. Send it on every later request. A missing session ID gets 400; an unknown one, or one issued to a different token, gets 404.

### Streaming responses

Replies are plain JSON, except for a `tools/call` whose tool reports progress or asks for elicitation while it runs. That call answers with an SSE stream carrying the progress notifications or elicitation requests, then the result. To receive it, send `Accept: application/json, text/event-stream`, and include a `progressToken` in `_meta` if you want progress. A call that sends nothing mid-run still gets plain JSON.

Elicitation is offered only to sessions whose client declared the `elicitation` capability at `initialize`. Answer an elicitation request with a separate `POST` in the same session; answers from another session are ignored.

## mDNS

| Field | Value |
|---|---|
| Service type | `_mcp._tcp.` |
| Service name | `droid-mcp-<Build.MODEL>`. Characters other than `A-Z a-z 0-9 -` become `-`, and the name is capped at 63 characters. |
| Port | The bound port: `TlsConfig.httpsPort` when TLS is on, otherwise `enableHttpServer(port = ...)` |

TXT records:

| Key | Values | Meaning |
|---|---|---|
| `version` | e.g. `0.11.0` | droid-mcp version on the device |
| `auth` | `bearer`, `none` | Whether a bearer token is required |
| `readonly` | `true`, `false` | Whether only read-only tools are served |
| `tls` | `true`, `false` | Whether the endpoint is HTTPS |

The token is never broadcast. To check from macOS:

```bash
dns-sd -B _mcp._tcp.
# Expect an instance such as "droid-mcp-Pixel-8"
```

## QR payload

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
| `url` | yes | Full endpoint URL. Starts with `https://` when TLS is on. |
| `token` | no | Bearer token (`DroidMcp.serverToken`). Omitted when auth is off. |
| `tls_fingerprint` | no | Present only when TLS is on. See [TLS](#tls). |
| `name` | yes | Display name (`Build.MODEL` in the sample app) |

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

| | Behavior |
|---|---|
| Generated | Without an explicit token, `build()` generates 32 `SecureRandom` bytes, encoded as URL-safe base64 without padding. The token survives `stopServer()` / `startServer()` on the same `DroidMcp` instance; a newly built instance gets a new one. |
| Custom | `enableHttpServer(token = ...)` sets a fixed token. It must be at least 16 characters, or the call throws `IllegalArgumentException`. |
| Rotation | `DroidMcp.rotateToken()` replaces the primary token and returns the new one. The old one stops working, so show a new QR code. |
| Per client | `DroidMcp.pairClient(label)` issues a separate token for one client and `revokeClient(label)` revokes it. `pairedClients()` lists them. Rotating the primary token doesn't affect them. |

## TLS

`enableTls(config)` serves HTTPS on `TlsConfig.httpsPort` (default 8443) instead of the plaintext port. The opt-in `droid-mcp-tls` module creates a self-signed certificate and saves it:

```kotlin
val tls = SelfSignedCert.loadOrCreate(File(context.filesDir, "droid-mcp-tls.p12"))
DroidMcp.builder()
    .enableHttpServer(context = context)
    .enableTls(tls)
```

- The certificate is self-signed (`CN=droid-mcp`, no SANs), so clients can't validate it through a CA chain. They should pin its SHA-256 fingerprint instead: `DroidMcp.tlsFingerprint`, which is the QR code's `tls_fingerprint`.
- The fingerprint is colon-separated uppercase hex of the certificate's DER encoding.
- Reusing the same keystore file keeps the fingerprint stable across restarts.

## Read-only mode

`enableHttpServer(readOnly = true)` lists only tools annotated `readOnlyHint`. Calling any other tool returns an MCP content error (`isError: true`, `"Tool '<name>' is not available in read-only mode"`).

> [!IMPORTANT]
> mDNS makes the device discoverable to everyone on the network. On any network you don't fully trust, keep `requireAuth = true`.
