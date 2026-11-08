# droid-mcp

Android MCP SDK (160+ phone capabilities as tools, one module per area) plus `:app`, an installable "MCP server for your phone". Tools serve on-device models (in-process) and desktop MCP clients (HTTP server on the phone).

## Quick reference

- Kotlin 2.4, Gradle 9.8, AGP 9 with built-in Kotlin (modules don't apply `kotlin.android`). minSdk 28; libraries compile against 36, `app` and `sample-app` against 37
- Before committing: `./gradlew assembleDebug testDebugUnitTest apiCheck lintDebug` (what CI runs). On Windows the qr, mlkit and files path tests fail (drive letters, symlinks); CI is Linux
- Opt-in modules, kept out of `droid-mcp-all` for their dependencies: `shizuku`, `root`, `audit`, `tls`, `server-service`
- Module list: `settings.gradle.kts`; tools per module: `docs/TOOLS.md`
- `sample-app` is a minimal one-screen SDK example (core, device, calendar); `app` is the product and the reference for services and special access

## Conventions

- Every tool implements `McpTool`. Its `description` is what a model reads: one or two plain sentences that start with a verb, say what comes back, and name the sibling tool when two overlap (`tap` vs `find_and_tap`). No Android API names unless the model needs them
- Every tool declares `outputSchema` matching exactly what `execute()` returns on success: `nullable = true` for nullable values, `optional = true` for keys only sometimes present, no entry for a key passed to `withImage()`
- Every module has a provider object (`CalendarTools`) with `all(context)`, `requiredPermissions()`, `hasPermissions(context)`. Modules with browsable data also have `resources(...)`: a `ResourceProvider` that checks its own permissions, returns empty instead of throwing, and sets `requiredTools` so gating the tool hides the resource
- Limits: `(params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10`, declared with `minimum`/`maximum`. Fixed choices declare `enumValues`. Keep schema and `execute()` identical
- Shared helpers live in `io.droidmcp.core.support` (`ActivityLaunch`, `SqlLike`, `StrictDates`, `CalendarSupport`, `UsageAccess`, `AppLabels`, `CompletionSupport`); use them instead of module copies
- Tools run on `Dispatchers.IO` with a timeout (`ToolRegistry`). Long tools call `reportProgress(...)`; tools needing input call `elicit(...)` (null when unsupported)
- ContentResolver: `?.use { cursor -> }`, paging in the loop. File paths go through `PathValidator` (external storage only)
- Imports, not inline fully qualified names

## Don't

- Don't put `LIMIT`/`OFFSET` in ContentResolver `sortOrder`; not every device supports it
- Don't request permissions from library code; host apps own the permission UX
- Don't use `SmsManager.getDefault()`; use `context.getSystemService()` on API 31+
- Don't require media permissions for File API access on API 33+
- Don't loosen a security check without saying so in the PR: Origin/session/token checks, `PathValidator`, the web SSRF guard, the `send_intent` action and URI allowlists, `ShellPolicy.RECOMMENDED`, the `run_shell` allowlist rules, password-field refusal in `get_text_around_cursor`. The model is in `docs/SECURITY.md`

## Adding a tool module

1. `droid-mcp-{name}/` with `build.gradle.kts` (android library, `implementation(project(":droid-mcp-core"))`, copy an existing module), a manifest with its permissions, the tools and a provider object
2. Add it to `settings.gradle.kts`, `droid-mcp-all`, `app` (`ServerController`, `ToolCatalog`, and `Capabilities` if it needs a permission), the README module table and `docs/TOOLS.md`
3. `./gradlew :droid-mcp-{name}:apiDump` and `./gradlew :droid-mcp-all:testDebugUnitTest -PupdateToolContract`, commit the `api/` changes

## API change checks

`apiCheck` compares each library's public API with `<module>/api/*.api`; `ToolContractTest` compares tool names, parameters, annotations and result keys with `droid-mcp-all/api/tool-contract.txt`. Regenerate them (commands above) for intended changes. Pre-1.0, changes are allowed; list anything users must act on under "Before you upgrade" in the release notes (`docs/VERSIONING.md`).

## The app (`:app`)

- **MVI:** each screen is `feature/<name>/` with `*Contract.kt` (State, Intent, Effect, pure reducers), `*ViewModel.kt` (extends `MviViewModel`) and `*Screen.kt`. The UI only sends intents; one-off events are effects. Pure functions and ViewModels are unit-tested with fakes (`app/src/test`)
- **No DI framework:** `AppGraph` (built in `DroidMcpApp`) wires everything; `context.appGraph` reaches it from services
- **Server:** `ServerController` builds and owns the one `DroidMcp`; `McpServerService` only hosts it, so it outlives the UI and START_STICKY restarts rebuild it. Permission changes add or remove tools live (`syncTools`). Settings baked in at build time (port, TLS, read-only, file writes, approvals, shell policy, token) show "Restart to apply"
- **Trusted Wi-Fi:** off a trusted network the listener pauses but the service stays up, because Android won't start a foreground service from the background. The network flow (`AppGraph.networkUpdates`) also re-reads on Wi-Fi permission and Location changes and when the app comes to the front. `NetworkAddress` keeps a network's name once read, since Android hides it from background apps and callbacks fire often. After a reconnect in the background the name stays hidden, so the server pauses until the app opens, unless the user granted "All the time" location (offered only by a Home banner after such a pause, never in Setup)
- **Permissions:** `Capabilities.kt` maps plain names ("Calendar", "Screen control") to permissions or Settings pages; `ToolCatalog` gates tool groups on them
- **Approvals:** `ConfirmationCenter` is the `ToolCallConfirmer`: an in-app dialog when visible, otherwise a notification. "Always" is saved, "1 hour" is memory only (`ApprovalPolicy`)
- **Per-computer tokens:** saved as `PairedComputer`s and restored on every build with `DroidMcp.restoreClient`
- **Design:** Material 3, fixed palette (no dynamic color), JetBrains Mono for technical text (`AppTheme.code`), shared pieces in `ui/components`
- **Release:** R8 with broad keeps for Netty, Ktor and BouncyCastle; per-ABI APKs (~45 MB). Signing from `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; CI decodes `KEYSTORE_BASE64`. After release-config changes, install the release APK and make real MCP calls; R8 breakage only shows at runtime

## Testing on a device

- Use a dedicated emulator. `adb install -g` grants runtime permissions; special access: `settings put secure enabled_accessibility_services …`, `cmd notification allow_listener …`, `ime enable/set …`, `appops set … SYSTEM_ALERT_WINDOW allow`
- `uiautomator dump` (and any UiAutomation client) unbinds accessibility services while it runs, so the app briefly sees Screen control as off. It also fails silently during animations and leaves the old dump file behind. Prefer screenshots and coordinates
- Android hides app overlays over system Settings and permission dialogs; test `guide_user` over a normal app
- The SSID (trusted Wi-Fi) needs precise location, and Android hides it from a background app (after boot or a network change) unless something keeps the process in use, such as the bound accessibility service, or the app has "All the time" location. Opening the app reads it again. `pm revoke ... ACCESS_BACKGROUND_LOCATION` resets the Home banner test
- `am force-stop` turns off the app's accessibility service; re-enable it before testing anything that needs it
- Keep `Application.onCreate` light (`AppGraph` builds heavy things lazily): after boot a slow start is killed as an ANR, which also blocks the boot-time server
- On the API 35 emulator, `screencap` returns black frames while our screen capture is active; stop the capture (force-stop) to take screenshots again
