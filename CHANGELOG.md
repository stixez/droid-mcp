# Changelog

All notable changes to droid-mcp will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.4.0] - 2026-05-15

### Added
- `droid-mcp-mlkit` module: on-device `recognize_text`, `label_image`, `detect_faces` tools (Google ML Kit, no network)
- mDNS broadcast (`_mcp._tcp`) with version/auth/readonly TXT records so desktop MCP clients can auto-discover the phone on the local network
- QR pairing in sample app — encodes URL, bearer token, and device name in a single scan; payload schema documented in `docs/PAIRING.md`
- MCP tool annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`) applied per category — read/get/list/search are readOnly+idempotent, send/write to external state are destructive, web tools are openWorld; surfaced under each entry in `tools/list`
- `.github/workflows/ci.yml` and `release.yml` for CI builds and automated GitHub Releases from CHANGELOG sections

### Changed
- `HttpTransport` now requires a bearer token by default; one is auto-generated with `SecureRandom` if not supplied and exposed via `DroidMcp.serverToken`
- New `enableHttpServer(readOnly = true)` flag exposes only read-only tools and rejects `tools/call` for destructive tools with an MCP content error (`isError: true`)
- 401 responses include `WWW-Authenticate: Bearer realm="droid-mcp"`
- Sample app displays the bearer token with a copy-to-clipboard button

### Security
- Default-on auth closes accidental exposure of SMS / intents / camera / NFC writes on shared Wi-Fi
- `set_ringtone` no longer accepts `file://` URIs (carried over from 0.3.x review; reaffirmed)
- mDNS does not advertise the bearer token; the token reaches the client out-of-band (QR or copy/paste)

## [0.3.0] - 2026-04-14

### Added
- 10 new modules: NFC, intent, playback, screenshot, DND, keyguard, wallpaper, ringtone, USB, print
- MCP Streamable HTTP transport for Claude Code integration
- JitPack publishing via `jitpack.yml`

## [0.2.0] - 2026-04-12

### Added
- 9 new modules: telephony, vibration, flashlight, biometric, network, sensors, QR, camera, audio
- Sample app integration for all 30 modules

### Fixed
- Resource leaks in cursor handling
- Fully-qualified-name imports cleaned up
- Biometric availability logic
- Scanner cleanup in QR module

## [0.1.0] - 2026-04-12

### Added
- Initial release: core MCP protocol implementation (JSON-RPC 2.0)
- In-process transport for on-device LLMs
- HTTP transport (Ktor) for desktop MCP clients
- Modules: device, calendar, contacts, SMS, files, notifications, call log, media, location, health, clipboard, apps, alarms, settings, bluetooth, wifi, downloads, screen, TTS, web, all
- Sample app with Compose UI

[Unreleased]: https://github.com/stixez/droid-mcp/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/stixez/droid-mcp/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/stixez/droid-mcp/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/stixez/droid-mcp/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/stixez/droid-mcp/releases/tag/v0.1.0
