# Security model

What protects tool calls, where the trust boundaries are, and what's out of scope.

## Transports

- **In-process** (on-device model): no socket. The host app is the boundary; anything in its process can call any registered tool.
- **HTTP** (desktop clients): everything below.

On both, calls run off the main thread with a timeout (default 5 min), and `confirmToolCalls` can require approval first.

## An attacker on the same network

The server listens on every interface, so anyone on the same Wi-Fi can reach it.

| Attack | Defense |
|---|---|
| Call tools without credentials | Bearer token, on by default: random 32 bytes, or 16+ characters if you supply one. Compared in constant time. |
| A web page on the phone calls `127.0.0.1:8080` (drive-by, DNS rebinding) | Foreign `Origin` gets 403; only `application/json` bodies are accepted, which rules out CORS "simple" requests. |
| Use another client's session | Sessions are tied to the token that created them. |
| Sniff traffic | Optional TLS with a pinned self-signed certificate. Without it, treat the network as trusted. |
| Reuse a leaked token | `rotateToken()`, or revoke one client's token (`revokeClient`). |
| Reach files outside shared storage | Paths are resolved, symlinks included, and refused outside external storage. Write tools are opt-in. See [Files](TOOLS.md#files). |
| Change things on an observe-only setup | `readOnly = true` lists and runs only read-only tools. |
| Call a tool you didn't mean to expose | `setDisabledTools` hides it from listing and calling; its resources and prompts disappear too. |
| Find the server | mDNS advertises version, auth, read-only and TLS flags, never the token. `/health` needs auth. |
| Exhaust resources | 4 MB body cap, capped sessions, call timeouts, shell output caps. |

> [!NOTE]
> Read-only means "changes nothing", not "harmless". Reading messages and taking silent screenshots are reads. Turn those tools off if a client shouldn't have them.

## Prompt injection

Web pages, messages, notifications, the clipboard and screen text can contain instructions for the model. droid-mcp returns them as data but can't stop a model from obeying. Two defenses:

- **Confirm on the phone:** `confirmToolCalls { request -> askUser(request) }` runs before every tool that changes something (`destructiveHint`) or reaches the internet (`openWorldHint`), since a fetched URL can carry data the model just read. The host app also refuses tools that tap, type, press notification buttons or draw overlays while an approval is waiting, so a model can't answer its own approval, and `invoke_notification_action` never acts on the host's own notifications. Decline, error or timeout returns `tool_call_declined` and the tool never runs.
- **Confirm on the client:** `ToolCallConfirmer.viaClientElicitation(fallback)` asks the desktop user through MCP elicitation, for clients that support it. It catches a misled model, not a compromised client; for that, confirm on the phone.

## What the host app owns

- **The token.** Whoever sees it (in a QR code, say) has access.
- **Permissions.** The library never requests them; tools return an error without them.
- **Special access.** Accessibility, notification access, keyboard, Shizuku and root are broad. Any authenticated call gets that authority, so include only the modules you need. Shell tools are default-deny and have their own [safety controls](SHELL.md#safety).
- **The audit log**, if you use `droid-mcp-audit`. It stores call arguments (message text, names, paths) unencrypted in app-private storage for 7 days by default. `clear()` and `exportJson()` are yours to expose, or implement `AuditSink` yourself and keep nothing.

## Out of scope

- a malicious or compromised host app
- a caller that has the token (limit what it can reach with read-only mode, gating and confirmations)
- physical access to the device
- third-party dependencies (Shizuku, libsu, Room, BouncyCastle) beyond pinned versions

## Reporting a vulnerability

Open a private security advisory on GitHub, not a public issue.
