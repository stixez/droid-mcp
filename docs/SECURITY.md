# droid-mcp Security Model

droid-mcp lets an LLM — on-device or remote — invoke phone capabilities through the Model Context Protocol. That is a lot of authority to hand out, so this document is explicit about what protects it, what the trust boundaries are, and what is deliberately **out of scope**.

## Transports and their boundaries

droid-mcp exposes tools over two transports:

| Transport | Who uses it | Boundary |
|-----------|-------------|----------|
| **In-process** (`InProcessTransport`) | An on-device LLM in the same app process | No network. The host app *is* the trust boundary — anything in the process can call any registered tool. Not audited. |
| **HTTP** (`HttpTransport`) | A desktop / remote MCP client over the LAN | Bearer-token auth (default on), optional TLS, optional read-only mode, per-tool gating, audit log. This is the surface the rest of this document is about. |

If you only use the in-process transport, the HTTP threat model below does not apply — no socket is ever opened.

## Threat model — attacker on the same network

Assume an attacker who can reach the device's IP on the LAN (same WiFi: a café, an office, a compromised router). What can they do, and what stops them?

| Attack | Without mitigation | Mitigation |
|--------|--------------------|------------|
| Call tools without credentials | Full control of the device surface | **Bearer auth is on by default** (`requireAuth = true`). A 32-byte `SecureRandom` token is generated if you don't supply one. 401 + `WWW-Authenticate: Bearer` on failure. Token compared in constant time. |
| Sniff the token / message contents in transit | Plaintext HTTP exposes the token and every argument (message bodies, contacts, locations) to a passive listener | **TLS** (`enableTls`, opt-in `droid-mcp-tls`). Self-signed cert; clients **pin the SHA-256 fingerprint** from the pairing QR (`DroidMcp.tlsFingerprint`) rather than trusting a CA chain. Without TLS, treat the network as trusted. |
| Replay a captured token | Impersonate the client indefinitely | `rotateToken()` invalidates the primary; per-client pairing (`pairClient` / `revokeClient`) lets you revoke one client without disturbing others. |
| Invoke destructive tools on an observe-only deployment | Send SMS, force-stop apps, etc. | **Read-only mode** (`readOnly = true`) filters `tools/list` to read-only tools and rejects `tools/call` for the rest. |
| Call a tool you never intended to expose | Whole registered surface is reachable | **Per-tool gating** (`setToolEnabled` / `setDisabledTools`) removes a tool from both `tools/list` and `tools/call` at runtime. |
| Discover the server | — | mDNS (`_mcp._tcp`) advertises version / auth mode / readonly / tls. It does **not** advertise the bearer token. |

## Host-app trust assumptions

droid-mcp is a library; the host app is responsible for the parts a library can't own:

- **Token distribution.** The pairing QR carries the token (and TLS fingerprint). Anyone who sees the QR gets the credential — show it only to the intended client.
- **Permission UX.** Library modules never request Android runtime permissions; the host does. A tool degrades gracefully (clear error) when its permission is missing.
- **Special-access grants.** Accessibility, Notification Listener, IME, Shizuku, and root are extreme grants. Once granted, a tool call that clears the bearer-auth boundary gets that authority. Only enable the tiers you need.
- **The audit DB.** If you use `droid-mcp-audit`, you own its lifecycle (below).

## Power tiers — escalating authority

Tiers 4 (Shizuku) and 5 (root) hand the LLM `shell`-UID or root-UID reach. The `run_shell` escape hatch is **default-deny**: the LLM cannot run an arbitrary command unless the host registers a prefix allowlist (`ShellAllowlist.set(...)`). Keep that allowlist as narrow as the app actually needs. Granting Shizuku/root to a host app is equivalent to granting it `adb shell` / `su` — treat it that way.

## Audit log privacy

`RoomAuditSink` persists every HTTP `tools/call`, **including the arguments** — which are the sensitive data (message text, contact names, file paths, coordinates). The database lives in the host app's private storage. The host owns:

- **Retention** — default 7 days; set `Duration.ZERO` to keep indefinitely.
- **Deletion** — `clear()`; export with `exportJson()`.
- **Encryption** — not applied by default. If the threat model includes device compromise, layer on encrypted storage.

The dependency-free `AuditSink` hook in core is the alternative: receive each record and decide yourself whether/where to persist. Persisting nothing is a valid choice.

## Out of scope

droid-mcp does **not** defend against:

- **A malicious or compromised host app.** The host has in-process access to every tool by construction.
- **A malicious LLM that has already cleared bearer auth.** Auth gates *who* can call; it doesn't reason about *intent*. Use read-only mode and per-tool gating to shrink what a trusted-but-wrong model can reach.
- **Physical device access.**
- **Supply-chain integrity of third-party deps** (Shizuku, libsu, Room, BouncyCastle) beyond pinning their versions.

## Packaging note (TLS)

BouncyCastle ships an identical `META-INF/versions/9/OSGI-INF/MANIFEST.MF` in all three of its jars. A host that depends on `droid-mcp-tls` must exclude it (it's unused on Android):

```kotlin
android {
    packaging { resources { excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" } }
}
```

## Reporting

Found a vulnerability? Open a private security advisory on the GitHub repository rather than a public issue.
