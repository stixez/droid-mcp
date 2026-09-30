# droid-mcp Security Model

droid-mcp lets an LLM, on-device or remote, call phone capabilities through MCP. This page covers what protects those calls, where the trust boundaries are, and what is **out of scope**.

## Transports and their boundaries

droid-mcp exposes tools over two transports:

| Transport | Who uses it | Boundary |
|-----------|-------------|----------|
| **In-process** (`InProcessTransport`) | An on-device LLM in the same app process | No network. The host app *is* the trust boundary — anything in the process can call any registered tool. Not audited. |
| **HTTP** (`HttpTransport`) | A desktop MCP client on the network | Bearer auth (on by default), Origin and session checks, optional TLS, read-only mode, per-tool gating, and an optional audit log. The rest of this page is about this transport. |

If you only use the in-process transport, no socket is opened and the HTTP threat model doesn't apply. Tool calls on both transports run on `Dispatchers.IO` with a timeout (`Builder.toolTimeout`, default 5 min).

## Threat model — attacker on the same network

Assume an attacker who can reach the device's IP, for example on the same Wi-Fi. The server listens on all interfaces of its port, so any network the phone is on counts.

| Attack | Without mitigation | Mitigation |
|--------|--------------------|------------|
| Call tools without credentials | Full control of the registered tools | **Bearer auth is on by default** (`requireAuth = true`). A 32-byte `SecureRandom` token is generated if you don't supply one, and one you do supply must be at least 16 characters. Tokens are compared in constant time. Failures get 401 with `WWW-Authenticate: Bearer`. |
| A web page in the phone's browser calls `http://127.0.0.1:8080/mcp` (drive-by or DNS rebinding) | Tool calls from any site the user visits, most dangerous with auth off | Requests whose `Origin` isn't in `allowedOrigins` get 403. Only `application/json` bodies are accepted, which rules out CORS "simple" requests. |
| Reuse another client's session | Act inside a session you didn't open | Session IDs are tied to the token label that created them. A foreign or missing `Mcp-Session-Id` is rejected. |
| Sniff the token / message contents in transit | Plaintext HTTP exposes the token and every argument (message bodies, contacts, locations) to a passive listener | **TLS** (`enableTls`, opt-in `droid-mcp-tls`). Self-signed cert; clients **pin the SHA-256 fingerprint** from the pairing QR (`DroidMcp.tlsFingerprint`) rather than trusting a CA chain. Without TLS, treat the network as trusted. |
| Reuse a leaked token | Impersonate the client until the token changes | `rotateToken()` replaces the primary token. With per-client pairing (`pairClient` / `revokeClient`) you can revoke one client without affecting the others. |
| Invoke destructive tools on an observe-only deployment | Send SMS, force-stop apps, etc. | **Read-only mode** (`readOnly = true`) filters `tools/list` to read-only tools and rejects `tools/call` for the rest. "Read-only" means "changes nothing", not "harmless": SMS, contacts and silent screenshots (`take_screenshot_via_a11y`, `capture_screen_quiet`) are still reads. Gate those individually if the client shouldn't see them. |
| Call a tool you never intended to expose | Whole registered surface is reachable | **Per-tool gating** (`setToolEnabled` / `setDisabledTools`) removes a tool from both `tools/list` and `tools/call` at runtime. |
| Discover the server | — | mDNS (`_mcp._tcp`) advertises `version`, `auth`, `readonly` and `tls`, but **not** the token. `/health` requires auth. |
| Exhaust memory or threads | Crash the host app | Bodies over 4 MB get 413. Tool calls time out. Shell output is capped at 4 MiB. |

## Host-app trust assumptions

droid-mcp is a library; the host app is responsible for the parts a library can't own:

- **Token distribution.** The pairing QR carries the token (and TLS fingerprint). Anyone who sees the QR gets the credential — show it only to the intended client.
- **Permission UX.** Library modules never request Android runtime permissions; the host does. A tool returns an error when its permission is missing.
- **Special-access grants.** Accessibility, Notification Listener, IME, Shizuku, and root are extreme grants. Once granted, a tool call that clears the bearer-auth boundary gets that authority. Only enable the tiers you need.
- **The audit DB.** If you use `droid-mcp-audit`, you own its lifecycle (below).

## Shizuku and root

`droid-mcp-shizuku` and `droid-mcp-root` give the LLM reach as the `shell` UID or as root. The `run_shell` escape hatch is **default-deny**: the LLM cannot run an arbitrary command unless the host registers an allowlist (`ShellAllowlist.set(...)`). Entries match the command's argv token-by-token, and entries that start with an interpreter (`sh`, `toybox`, `su`, `app_process`, `env`, …) are rejected. `ShellPolicy` can additionally deny sensitive setting keys and permission grants. Keep the allowlist as narrow as the app needs. Granting Shizuku or root to a host app is the same as granting it `adb shell` or `su`. Details: [SHIZUKU.md](SHIZUKU.md), [ROOT.md](ROOT.md).

## Audit log privacy

`RoomAuditSink` persists every HTTP `tools/call`, **including the arguments** — which are the sensitive data (message text, contact names, file paths, coordinates). The database lives in the host app's private storage. The host owns:

- **Retention** — default 7 days; set `Duration.ZERO` to keep indefinitely.
- **Deletion** — `clear()`; export with `exportJson()`.
- **Encryption** — not applied by default. If the threat model includes device compromise, layer on encrypted storage.

The dependency-free `AuditSink` hook in core is the alternative: receive each record and decide yourself whether/where to persist. Persisting nothing is a valid choice.

## Out of scope

droid-mcp does **not** defend against:

- **A malicious or compromised host app.** The host has in-process access to every tool by construction.
- **A malicious LLM that has already cleared bearer auth.** Auth decides *who* can call, not *why*. Use read-only mode and per-tool gating to limit what a trusted but mistaken model can reach.
- **Prompt injection through tool output.** Web pages, SMS, notifications, clipboard contents and screen text are untrusted, and can tell the model to call other tools. droid-mcp returns that content as data but can't stop a model from obeying it. Gate destructive tools, or have the user approve them with `Builder.confirmToolCalls { request -> showDialog(request) }`. By default it asks for every tool marked `destructiveHint`, on both transports, and a decline or no answer means the tool never runs.
- **Physical device access.**
- **Supply-chain integrity of third-party deps** (Shizuku, libsu, Room, BouncyCastle) beyond pinning their versions.

## Packaging note (TLS)

BouncyCastle and Netty ship duplicate `META-INF` files. The exclude list is in the [README](../README.md#installation).

## Reporting

Found a vulnerability? Open a private security advisory on the GitHub repository rather than a public issue.
