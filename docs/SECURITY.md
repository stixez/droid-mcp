# Security model

droid-mcp lets an LLM, on the device or on a desktop, call phone capabilities through MCP. This page covers what protects those calls, where the trust boundaries are, and what is out of scope.

## Transports

| Transport | Used by | Boundary |
|---|---|---|
| In-process (`InProcessTransport`) | An on-device LLM in the same app process | No socket. The host app is the trust boundary: anything in the process can call any registered tool. Not audited. |
| HTTP (`HttpTransport`) | A desktop MCP client on the network | Bearer auth (on by default), Origin and session checks, optional TLS, read-only mode, per-tool gating and an optional audit log. Most of this page is about this transport. |

On both transports, tool calls run on `Dispatchers.IO` with a per-call timeout (`Builder.toolTimeout()`, default 5 min), and `Builder.confirmToolCalls` can require approval before a tool runs.

## Threat model: an attacker on the same network

Assume an attacker who can reach the device's IP, for example on the same Wi-Fi. The server listens on all interfaces, so every network the phone is on counts.

| Attack | Mitigation |
|---|---|
| Call tools without credentials | Bearer auth is on by default (`requireAuth = true`). A 32-byte `SecureRandom` token is generated if you don't supply one; a token you supply must be at least 16 characters. Tokens are compared in constant time. Failures get 401 with `WWW-Authenticate: Bearer realm="droid-mcp"`. |
| A web page in the phone's browser calls `http://127.0.0.1:8080/mcp` (drive-by or DNS rebinding) | A request whose `Origin` isn't in `allowedOrigins` gets 403. Only `application/json` bodies are accepted (415 otherwise), which rules out CORS "simple" requests. |
| Use another client's session | Session IDs are tied to the token that created them. A missing `Mcp-Session-Id` gets 400; an unknown or foreign one gets 404. |
| Sniff the token or tool arguments in transit | TLS (`enableTls`, with the opt-in `droid-mcp-tls` module). The certificate is self-signed, so clients pin its SHA-256 fingerprint from the pairing QR (`DroidMcp.tlsFingerprint`). Without TLS, treat the network as trusted. |
| Reuse a leaked token | `rotateToken()` replaces the primary token. Per-client tokens (`pairClient` / `revokeClient`) let you revoke one client without affecting the others. |
| Call write tools on an observe-only deployment | Read-only mode (`readOnly = true`) lists only read-only tools and rejects `tools/call` for the rest. |
| Call a tool you never meant to expose | `setToolEnabled` / `setDisabledTools` remove a tool from both `tools/list` and `tools/call` at runtime. |
| Discover the server | mDNS (`_mcp._tcp`) advertises `version`, `auth`, `readonly` and `tls`, never the token. `/health` requires auth. |
| A prompt-injected model sends SMS, deletes data or installs apps | `Builder.confirmToolCalls` runs a host confirmer, such as a dialog on the phone, before every `destructiveHint` tool on both transports. See [Prompt injection](#prompt-injection). |
| Exhaust memory or threads | Bodies over 4 MB get 413. Sessions are capped (least recently used evicted). Tool calls time out. Shell output is capped at 4 MiB per stream. |

> [!NOTE]
> Read-only means "changes nothing", not "harmless". Reading SMS or contacts and taking silent screenshots (`take_screenshot_via_a11y`, `capture_screen_quiet`) are reads. Disable those tools individually if the client shouldn't have them.

Protocol details (headers, status codes, SSE) are in [PAIRING.md](PAIRING.md#connecting).

## Prompt injection

Web pages, SMS, notifications, clipboard contents and screen text are untrusted, and can tell the model to call other tools. droid-mcp returns that content as data but can't stop a model from obeying it. Two defenses:

- **Confirm on the phone.** `Builder.confirmToolCalls { request -> showDialog(request) }` asks before every tool marked `destructiveHint` (pass `requiresConfirmation` to change the set). A decline, an exception or no answer within the tool timeout returns `tool_call_declined`, and the tool never runs.
- **Confirm on the client.** `ToolCallConfirmer.viaClientElicitation(fallback)` asks the desktop client's user through MCP elicitation. It works only for clients that declared the `elicitation` capability at `initialize` and accept an SSE response, and each elicitation is scoped to the session that made the call. Without that, `fallback` decides, and with no fallback the call is declined.

Confirming on the client catches a misled model but not a compromised client. For that, confirm on the phone.

## Host-app responsibilities

droid-mcp is a library. The host app owns what a library can't:

- **Token distribution.** The pairing QR carries the token and TLS fingerprint. Anyone who sees it has the credential, so show it only to the intended client.
- **Permission UX.** Library modules never request Android runtime permissions. A tool returns an error when its permission is missing.
- **Special-access grants.** Accessibility, Notification Listener, IME, Shizuku and root are broad grants. Once granted, any tool call that passes bearer auth gets that authority. Enable only the modules you need.
- **The audit database**, if you use `droid-mcp-audit` (see below).

## Shizuku and root

`droid-mcp-shizuku` and `droid-mcp-root` run tools as the `shell` UID or as root. Granting either to a host app is equivalent to giving it `adb shell` or `su`.

- `run_shell` is default-deny. It runs nothing until the host registers an allowlist with `ShellAllowlist.set(...)`. Entries match argv token by token, and entries that start with a shell or interpreter (`sh`, `toybox`, `su`, `app_process`, `env` and others) are rejected.
- `ShellPolicy.RECOMMENDED` is the default. It blocks setting keys and permission grants that would hand over control of the device (accessibility, IME, ADB, `WRITE_SECURE_SETTINGS` and similar). `ShellPolicy.PERMISSIVE` is an explicit opt-in.

Details: [SHELL.md](SHELL.md#safety).

## Audit log privacy

`RoomAuditSink` stores every HTTP `tools/call`, including its arguments, which are the sensitive part (message text, contact names, file paths, coordinates). The database lives in the host app's private storage, and the host owns:

- **Retention.** Default 7 days. `Duration.ZERO` keeps rows indefinitely.
- **Deletion and export.** `clear()` and `exportJson()`.
- **Encryption.** Not applied. If your threat model includes device compromise, add encrypted storage.

The dependency-free `AuditSink` interface in core is the alternative: receive each record and decide yourself whether and where to keep it. Keeping nothing is a valid choice.

## Out of scope

droid-mcp does not defend against:

- **A malicious or compromised host app.** The host has in-process access to every tool by design.
- **A malicious caller that has passed bearer auth.** Auth decides who can call, not why. Use read-only mode, per-tool gating and `confirmToolCalls` to limit what a trusted but mistaken model can reach.
- **Physical access to the device.**
- **Supply-chain integrity of third-party dependencies** (Shizuku, libsu, Room, BouncyCastle) beyond pinning their versions.

## Reporting a vulnerability

Open a private security advisory on the GitHub repository rather than a public issue.
