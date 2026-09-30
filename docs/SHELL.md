# Shell tools: Shizuku and root

`droid-mcp-shell-core` defines 17 tools that run privileged shell commands: install and uninstall apps, grant permissions, write system settings, take a screenshot without a consent prompt, and run allowlisted commands. Two backend modules run them:

- `droid-mcp-shizuku` runs them as the `shell` UID (the same identity as `adb shell`) through the [Shizuku](https://github.com/RikkaApps/Shizuku) service. No root is needed.
- `droid-mcp-root` runs them as the `root` UID through `su`, using [libsu](https://github.com/topjohnwu/libsu).

Both register the same tool names, parameters and results. Neither is part of `droid-mcp-all`.

> [!IMPORTANT]
> Anyone who can call these tools gets the backend's UID. Read [Safety](#safety) before you register them.

## Shizuku or root

| | Shizuku | Root |
|---|---|---|
| Runs as | `shell` UID | `root` UID |
| Device requirement | Shizuku app, started via wireless debugging (Android 11+) or ADB | Rooted, with a superuser manager such as Magisk or KernelSU |
| Grant | Shizuku permission dialog | Superuser manager prompt |
| Survives reboot | No, restart Shizuku after each boot | Yes |
| Extra dependency | `dev.rikka.shizuku:api` + `:provider` 13.1.5 | `com.github.topjohnwu.libsu:core` + `:io` 6.0.0 (JitPack) |

The 17 tools behave the same on both. What root adds (`pm hide`, writing to `/system`, reading `/data/data/<pkg>`) is reachable only through `run_shell`, and only for commands you allowlist. If you don't need that, use Shizuku.

## Setup: Shizuku

1. Add the dependency. It brings in `droid-mcp-shell-core` and the Shizuku API.

   ```kotlin
   implementation("com.github.stixez.droid-mcp:droid-mcp-shizuku:0.11.0")
   ```

2. Declare the Shizuku provider in the host manifest. The module does not declare it for you, because the authority must be scoped to your application ID:

   ```xml
   <provider
       android:name="rikka.shizuku.ShizukuProvider"
       android:authorities="${applicationId}.shizuku"
       android:multiprocess="false"
       android:enabled="true"
       android:exported="true"
       android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
   ```

   The module's own manifest adds a `<queries>` entry for `moe.shizuku.privileged.api`, so on API 30+ `permissionStatus()` can tell "not installed" from "not running".

3. Install the Shizuku app ([Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [GitHub releases](https://github.com/RikkaApps/Shizuku/releases)) and start its service, via wireless debugging on Android 11+ or via ADB on any version. The Shizuku app shows the exact steps for your device.

4. Request permission. `ShizukuTools.requestPermission(requestCode)` returns nothing; the result arrives only at listeners registered with `Shizuku.addRequestPermissionResultListener`.

   ```kotlin
   class MainActivity : ComponentActivity() {
       private val shizukuListener = Shizuku.OnRequestPermissionResultListener { requestCode, result ->
           if (requestCode == SHIZUKU_REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) {
               // Shizuku is usable now: rebuild or refresh your tool registration.
           }
       }

       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           Shizuku.addRequestPermissionResultListener(shizukuListener)
       }

       override fun onDestroy() {
           Shizuku.removeRequestPermissionResultListener(shizukuListener)
           super.onDestroy()
       }

       private fun onGrantShizukuClicked() {
           when {
               ShizukuTools.isShizukuReady() -> Unit // already usable
               Shizuku.pingBinder() -> ShizukuTools.requestPermission(SHIZUKU_REQUEST_CODE)
               else -> startActivity(ShizukuTools.installOrOpenIntent(this)) // opens Shizuku, or its Play Store page
           }
       }

       companion object {
           private const val SHIZUKU_REQUEST_CODE = 0xCAFE
       }
   }
   ```

5. Register the tools:

   ```kotlin
   val mcp = DroidMcp.builder()
       .addTools(ShizukuTools.all(context)) // ShellPolicy.RECOMMENDED by default
       .build()
   ```

The module ships `consumer-rules.pro`, so minified (R8) host builds need no extra rules. The backend calls Shizuku's private `Shizuku.newProcess` through reflection, and those rules keep it and `ShizukuRemoteProcess`.

## Setup: root

1. Add the dependency. It brings in `droid-mcp-shell-core` and libsu. libsu is served from JitPack, so keep `maven { url = uri("https://jitpack.io") }` in your repositories. The module declares no Android permissions.

   ```kotlin
   implementation("com.github.stixez.droid-mcp:droid-mcp-root:0.11.0")
   ```

2. Root the device with a superuser manager that provides `su`, such as Magisk or KernelSU. That setup is outside droid-mcp's scope.

3. Request root and register the tools:

   ```kotlin
   RootTools.requestAccess { granted ->
       // Runs on libsu's worker thread: switch to the main thread before touching UI.
       if (granted) runOnUiThread { /* rebuild or refresh your tool registration */ }
   }

   val mcp = DroidMcp.builder()
       .addTools(RootTools.all(context)) // ShellPolicy.RECOMMENDED by default
       .build()
   ```

   `requestAccess` calls libsu's `Shell.getShell { }` without blocking, which makes the superuser manager show its prompt. The callback receives `true` when the shell is a root shell. It is safe to call more than once.

> [!NOTE]
> Until `requestAccess` has run, libsu has not checked for root, so `isRootAvailable()` is `false` and every tool returns `shell_unavailable`. `requestAccess` uses libsu's default `Shell.Builder`: if your host calls `Shell.setDefaultBuilder(...)` with non-root flags first, the prompt never appears.

## Choosing a backend at runtime

The registry keeps the last tool registered under a name, so register one backend, not both. To prefer root and fall back to Shizuku, pick a backend at startup:

```kotlin
import io.droidmcp.core.DroidMcp
import io.droidmcp.root.RootShellBackend
import io.droidmcp.shell.NoOpShellBackend
import io.droidmcp.shell.ShellBackend
import io.droidmcp.shell.ShellPolicy
import io.droidmcp.shell.ShellTools
import io.droidmcp.shizuku.ShizukuShellBackend

// RootShellBackend.isAvailable() is false until RootTools.requestAccess() has completed.
val root = RootShellBackend()
val shizuku = ShizukuShellBackend()
val backend: ShellBackend = when {
    root.isAvailable() -> root
    shizuku.isAvailable() -> shizuku
    else -> NoOpShellBackend // every tool returns shell_unavailable
}
val mcp = DroidMcp.builder()
    .addTools(ShellTools.all(context, backend, ShellPolicy.RECOMMENDED))
    .build()
```

The sample app does the same with the providers: `RootTools.all(context, policy)` when root is already granted, otherwise `ShizukuTools.all(context, policy)`. Its **Grant Access** chips call the permission flows above and re-run `MainViewModel.initialize()` when a grant arrives.

## Checking status

The tools are always registered: `requiredPermissions()` is empty and `hasPermissions()` is always `true` for both providers. Each call checks the backend when it runs.

| | Shizuku | Root |
|---|---|---|
| Ready check | `ShizukuTools.isShizukuReady()`: binder answers a ping and this app has permission | `RootTools.isRootAvailable()`: `Shell.isAppGrantedRoot() == true` |
| Detailed status | `ShizukuTools.permissionStatus(context)`: `Granted`, or `NotGranted` for not installed, not running, or not granted. The first two carry the `installOrOpenIntent` intent. | `RootTools.permissionStatus(context)`: `Granted`, or `NotGranted` for not checked yet (`requestAccess` never called) or denied |
| `shell_unavailable` | Service not running, or it died during the call | `requestAccess` never called, or no `su` shell could be started |
| `shell_permission_denied` | Service running, permission not granted | Device not rooted, prompt denied, or root revoked |

## Tools

| Tool | Command | Parameters |
|---|---|---|
| `install_apk` | `pm install [-r]` | `path` (absolute, ends in `.apk`, no `..` segments), `replace` (default `true`) |
| `uninstall_app` | `pm uninstall [-k]` | `package_name`, `keep_data` (default `false`) |
| `clear_app_data` | `pm clear` | `package_name` |
| `force_stop_app` | `am force-stop` | `package_name` |
| `disable_app` | `pm disable-user --user 0` | `package_name` |
| `enable_app` | `pm enable` | `package_name` |
| `grant_permission` | `pm grant` | `package_name`, `permission` |
| `revoke_permission` | `pm revoke` | `package_name`, `permission` |
| `list_app_permissions` | `dumpsys package <pkg>` | `package_name` |
| `put_secure_setting`, `put_global_setting`, `put_system_setting` | `settings put <namespace>` | `key`, `value` (up to 4096 characters) |
| `get_top_window` | `dumpsys window` | none |
| `set_app_standby_bucket` | `am set-standby-bucket` | `package_name`, `bucket` (`active`, `working_set`, `frequent`, `rare`, `restricted`) |
| `make_app_inactive` | `am set-inactive <pkg> true` | `package_name` |
| `capture_screen_quiet` | `screencap -p [-d <display>]` | `display` (default `0`). Returns a base64 PNG with no MediaProjection prompt. |
| `run_shell` | Any command the host allowlists | `command`, `args` (argv array), `max_stdout_bytes` (1024–65536, default 8192, also applied to stderr) |

Only `get_top_window`, `list_app_permissions` and `capture_screen_quiet` are read-only. On an HTTP server with `readOnly = true`, they are the only shell tools listed or callable. Every other shell tool is marked `destructiveHint`, so `Builder.confirmToolCalls` asks before each of them by default.

### Error codes

| Code | Meaning |
|---|---|
| `shell_unavailable` | The backend isn't running or reachable, or it went away during the call (see [Checking status](#checking-status)) |
| `shell_permission_denied` | The backend is reachable but access isn't granted |
| `shell_spawn_failed` | The process could not be started, or it hit the backend timeout |
| `denied_by_policy` | Blocked by `ShellPolicy`. Nothing was spawned. |
| `run_shell_not_enabled` | The command doesn't match the `run_shell` allowlist. The detail lists the current allowlist. |
| `invalid_args`, `invalid_package_name`, `invalid_permission`, `invalid_settings_key`, `invalid_settings_value` | Input failed validation. Nothing was spawned. |

Each tool also has its own code for a failed command, such as `install_failed`, `grant_failed`, `settings_put_failed` or `screencap_failed`.

## Safety

The MCP server's bearer auth is the only boundary in front of these tools. Even without `run_shell`, the dedicated tools are enough to take over the device, which matters when a malicious prompt steers the model, for example through a notification, web page or message it reads:

- The `put_*_setting` tools can write any key: enable an accessibility service or notification listener, change the default IME, or turn on ADB.
- `grant_permission` can grant any permission the target app declares, including development permissions such as `WRITE_SECURE_SETTINGS`.
- `install_apk` silently installs any readable APK, and `uninstall_app`, `clear_app_data` and `disable_app` are destructive.

The host has the following controls. The model cannot change any of them.

### ShellPolicy

Pass a policy to `ShizukuTools.all(context, policy)`, `RootTools.all(context, policy)` or `ShellTools.all(context, backend, policy)`. It denies specific setting keys in every namespace (case-insensitive) and specific permissions for `grant_permission` (exact match). It does not affect `revoke_permission` or the package-manager tools.

| Policy | Denies |
|---|---|
| `ShellPolicy.RECOMMENDED` (default) | Keys in `RECOMMENDED_DENIED_SETTING_KEYS`: accessibility, notification-listener and notification-assistant, IME, ADB, developer-options, unknown-sources and package-verifier settings. Permissions in `RECOMMENDED_DENIED_PERMISSIONS`: `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `DUMP`, `PACKAGE_USAGE_STATS`, `INTERACT_ACROSS_USERS`, `SET_PROCESS_LIMIT`, `CHANGE_CONFIGURATION`. |
| `ShellPolicy.PERMISSIVE` | Nothing. Opt in only if the model is fully trusted. |
| `ShellPolicy(deniedSettingKeys, deniedPermissions)` | Your own sets. Start from the `RECOMMENDED_*` sets to extend them. |

### Register less

Filter the list from `all()` by tool name, gate tools at runtime with `DroidMcp.setToolEnabled` / `setDisabledTools`, or serve over HTTP with `readOnly = true`.

### run_shell allowlist

`run_shell` refuses every command until the host calls `ShellAllowlist.set(setOf(...))`. The allowlist is process-global.

- Entries match the request's argv token by token, as a prefix. `"pm list"` matches `pm list packages`, but not `pmx` or `pm listx`.
- Arguments after the matched prefix are not checked, so allowlist the narrowest prefix that works.
- `set` throws `IllegalArgumentException` for blank entries and for entries whose first token (or its basename) is a shell, interpreter or exec wrapper listed in `ShellAllowlist.FORBIDDEN_LEADING_COMMANDS`, such as `sh`, `toybox`, `busybox`, `su`, `app_process`, `env`, `xargs`, `timeout`, `awk` or `python`. The previous allowlist is kept.
- Commands that can start other commands from their arguments (`find -exec`, for example) are not blocked. Review each entry with that in mind.
- In argv form (`command` plus `args`), `command` must be a single token.
- The string form (`command` only) splits on whitespace and ignores quotes.

```kotlin
ShellAllowlist.set(setOf("pm list packages", "dumpsys battery", "settings get global"))
```

### install_apk paths

`install_apk` accepts only an absolute path that ends in `.apk`, contains no `..` segments and no control characters, so the value can't be parsed as a `pm install` option. There is no storage sandbox: any APK the backend can read can be installed, including one that requests dangerous permissions.

## Limits

Both backends take the same constructor parameters and defaults:

```kotlin
ShizukuShellBackend(execTimeoutMs = 30_000, maxOutputBytes = 4 * 1024 * 1024, maxBinaryOutputBytes = 64 * 1024 * 1024)
RootShellBackend(execTimeoutMs = 30_000, maxOutputBytes = 4 * 1024 * 1024, maxBinaryOutputBytes = 64 * 1024 * 1024)
```

`ShizukuTools` and `RootTools` use default-configured backends. To change the limits, build the tools yourself: `ShellTools.all(context, ShizukuShellBackend(execTimeoutMs = 10_000), policy)`.

| Limit | Behavior |
|---|---|
| Time | Each call has a 30 s wall-clock limit. On timeout or cancellation the process is killed and the call returns `shell_spawn_failed`. The core `ToolRegistry` timeout (default 5 min, `Builder.toolTimeout()`) also applies. |
| Output | stdout and stderr are each capped at 4 MiB. `capture_screen_quiet` gets 64 MiB of stdout. |
| Truncation | When a stream exceeds its cap, the process is killed and the call returns the output captured so far with exit code `-1`. `run_shell` reports this as `stdout_truncated` / `stderr_truncated`; the other tools treat it as a failed command. |
| stdin | Closed at spawn (Shizuku) or redirected from `/dev/null` (root), so a command that reads stdin sees EOF instead of hanging. |

Backend-specific behavior:

- **Shizuku.** If the Shizuku service dies during a call, the call returns `shell_unavailable`.
- **Root: one `su` shell per call.** Each call builds its own shell and closes it afterwards; it never uses libsu's shared main shell. A command that changes directory or environment, or exits, can't affect later calls. Some superuser managers log an entry or show a toast for each `su` session, so expect one per tool call.
- **Root: approximate output cap.** libsu delivers decoded lines, so the 4 MiB cap counts characters plus one per line rather than bytes.
- **Root: `capture_screen_quiet`.** libsu's text output would corrupt binary data, so this tool writes to a temp file in `/data/local/tmp` (created under `umask 077` and always deleted) and reads it back with `SuFileInputStream`, capped at 64 MiB.
