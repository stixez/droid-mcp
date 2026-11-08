# Connecting clients

How a desktop MCP client reaches the phone's server. With the droid-mcp app, **Copy command** on the home screen does all of this for you; this page is for SDK hosts and client authors.

## Endpoint

The server speaks MCP Streamable HTTP at `/mcp` (protocol 2025-11-25, 2025-06-18, 2025-03-26, 2024-11-05).

| Request | Behavior |
|---|---|
| `POST /mcp` | JSON-RPC. `Content-Type: application/json` (else 415), at most 4 MB (else 413). |
| `GET /mcp` | The session's event stream for `list_changed` notifications. Needs `Accept: text/event-stream` and a session ID. |
| `DELETE /mcp` | Ends the session. |
| `GET /health` | Tool count and read-only state. Needs the token. |

Every request is checked for:

1. **Origin.** A browser `Origin` not in `allowedOrigins` gets 403. Native clients send none.
2. **Protocol version.** An unsupported `MCP-Protocol-Version` gets 400.
3. **Token.** `Authorization: Bearer <token>`; missing or wrong gets 401.
4. **Session.** `initialize` returns `Mcp-Session-Id`; send it on every later request. Missing gets 400; unknown, or issued to another token, gets 404.

Replies are plain JSON, except a `tools/call` whose tool reports progress or asks for input while it runs. That reply is an event stream, if the client accepts `text/event-stream`. The `GET /mcp` stream sends a keep-alive every 25 s; opening a second one closes the first.

## Tokens

- **Generated:** without `enableHttpServer(token = ...)`, `build()` creates a random 32-byte token. It lasts as long as that `DroidMcp` instance. A token you pass in must be 16+ characters.
- **Rotate:** `rotateToken()` replaces it; the old one stops working at once.
- **Per client:** `pairClient(label)` issues a separate token and `revokeClient(label)` removes it. `restoreClient(...)` re-registers saved ones after a restart. Rotating the main token doesn't touch them.

## Discovery

With an Android `Context`, the server advertises `_mcp._tcp` over mDNS as `droid-mcp-<model>`. TXT records hold `version`, `auth`, `readonly` and `tls`. The token is never broadcast.

## TLS

`enableTls(config)` serves HTTPS on port 8443 instead of plain HTTP. The opt-in `droid-mcp-tls` module makes and keeps a self-signed certificate:

```kotlin
val tls = SelfSignedCert.loadOrCreate(File(context.filesDir, "droid-mcp-tls.p12"))
DroidMcp.builder().enableHttpServer(context = context).enableTls(tls)
```

Clients can't check a self-signed certificate against a CA, so they pin its SHA-256 fingerprint (`DroidMcp.tlsFingerprint`, colon-separated hex). The same keystore file keeps the same fingerprint.

## Read-only mode

`enableHttpServer(readOnly = true)` lists only tools marked `readOnlyHint`. Calling another tool returns `isError: true` with "not available in read-only mode".
