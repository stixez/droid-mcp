# Shizuku backend (Tier 4)

`droid-mcp-shizuku` runs the 17 shared shell tools from `droid-mcp-shell-core` as the `shell` UID (the same identity as `adb shell`), through the [Shizuku](https://github.com/RikkaApps/Shizuku) service. No root is needed.

This page also documents the shell tools themselves and their safety controls. The root backend runs the same tools as the `root` UID: see [ROOT.md](ROOT.md).

## 1. Add the dependency

```kotlin
implementation("com.github.stixez.droid-mcp:droid-mcp-shizuku:0.11.0")
```

It brings in `droid-mcp-shell-core` and Shizuku API `dev.rikka.shizuku:api` + `:provider` 13.1.5. The module is not part of `droid-mcp-all`.

## 2. Declare the Shizuku provider

The Shizuku client API needs its provider in the host manifest, under an authority scoped to your application ID. The module does not declare it for you:

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

The module's own manifest adds a `<queries>` entry for `moe.shizuku.privileged.api`. On API 30+, this lets `permissionStatus()` tell "not installed" apart from "not activated".

## 3. Install and start Shizuku

1. Install the Shizuku app: [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [GitHub releases](https://github.com/RikkaApps/Shizuku/releases).
2. Start the service from the Shizuku app. On Android 11+ you can use wireless debugging. On any version you can use ADB. The app shows the exact steps and the ADB command for your device.
3. Shizuku does not survive a reboot. Start it again after each boot.

## 4. Request permission and register the tools

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
            ShizukuTools.isShizukuReady() -> Unit                     // already usable
            Shizuku.pingBinder() -> ShizukuTools.requestPermission(SHIZUKU_REQUEST_CODE)
            else -> startActivity(ShizukuTools.installOrOpenIntent(this)) // opens Shizuku, or its Play Store page
        }
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 0xCAFE
    }
}
```

`ShizukuTools.requestPermission(requestCode)` calls `Shizuku.requestPermission` and returns nothing. The result is delivered only to listeners registered with `Shizuku.addRequestPermissionResultListener`. The sample app (`sample-app/.../MainActivity.kt`) uses this pattern for its **Grant Access** chip, and re-runs `MainViewModel.initialize()` when the grant arrives.

Register the tools:

```kotlin
val mcp = DroidMcp.builder()
    .addTools(ShizukuTools.all(context))   // ShellPolicy.RECOMMENDED by default
    // ...
    .build()
```

`ShizukuTools.requiredPermissions()` is empty and `hasPermissions()` is always `true`. The tools are always registered, and each call checks Shizuku at execution time.

## 5. Check status

- `ShizukuTools.isShizukuReady()` returns `true` when the binder answers a ping and this app has been granted permission.
- `ShizukuTools.permissionStatus(context)` returns `Granted`, or `NotGranted` with a message for one of three cases: not installed, installed but not running, or running but not granted. In the first two cases it also carries the `installOrOpenIntent` intent.

If the Shizuku service isn't running, every shell tool returns `shell_unavailable`. If it's running but this app hasn't been granted permission, they return `shell_permission_denied`. If the Shizuku service dies during a call, the call also returns `shell_unavailable`.

## Shell tools

The Shizuku and root backends expose the same names, parameters and results.

| Tool | Command | Parameters |
|---|---|---|
| `install_apk` | `pm install [-r]` | `path` (absolute, ends in `.apk`, no `..`), `replace` (default true) |
| `uninstall_app` | `pm uninstall [-k]` | `package_name`, `keep_data` (default false) |
| `clear_app_data` | `pm clear` | `package_name` |
| `force_stop_app` | `am force-stop` | `package_name` |
| `disable_app` | `pm disable-user --user 0` | `package_name` |
| `enable_app` | `pm enable` | `package_name` |
| `grant_permission` | `pm grant` | `package_name`, `permission` |
| `revoke_permission` | `pm revoke` | `package_name`, `permission` |
| `list_app_permissions` | `dumpsys package` | `package_name` |
| `put_secure_setting` / `put_global_setting` / `put_system_setting` | `settings put <ns>` | `key`, `value` |
| `get_top_window` | `dumpsys window` | none |
| `set_app_standby_bucket` | `am set-standby-bucket` | `package_name`, `bucket` (`active`, `working_set`, `frequent`, `rare`, `restricted`) |
| `make_app_inactive` | `am set-inactive <pkg> true` | `package_name` |
| `capture_screen_quiet` | `screencap -p` | `display` (optional). Returns a base64 PNG with no MediaProjection prompt. |
| `run_shell` | anything the host allowlists | `command`, `args` (argv array), `max_stdout_bytes` (1024–65536, default 8192) |

Only `get_top_window`, `list_app_permissions` and `capture_screen_quiet` are marked read-only. On an HTTP server with `readOnly = true`, those three are the only shell tools listed or callable.

Error codes:

| Code | Meaning |
|---|---|
| `shell_unavailable` | The backend isn't running or reachable (Shizuku stopped, root not checked yet), or it went away during the call |
| `shell_spawn_failed` | The process could not be run, or it hit the backend timeout |
| `shell_permission_denied` | Access isn't granted (Shizuku permission, or root on an unrooted or denied device) |
| `denied_by_policy` | Blocked by `ShellPolicy`. Nothing was spawned. |
| `run_shell_not_enabled` | The command does not match the `run_shell` allowlist |

Each tool also has its own `*_failed` code for non-zero exits, such as `install_failed` or `settings_put_failed`.

## Safety

Anyone who can reach these tools gets the backend's UID: `shell` here, `root` for [ROOT.md](ROOT.md). The MCP server's bearer auth is the only boundary in front of them. Even without `run_shell`, the dedicated tools are enough to take over the device. That matters when a malicious prompt steers the model, for example through a notification, web page or message it reads.

- The `put_*_setting` tools write any key. For example, they can enable an accessibility service or notification listener, change the default IME, or turn on ADB.
- `grant_permission` grants permissions the target app declares, including development permissions such as `WRITE_SECURE_SETTINGS`.
- `install_apk` installs any readable APK silently. There is no storage sandbox. `uninstall_app`, `clear_app_data` and `disable_app` are destructive.

The host has these controls. The model cannot change any of them.

- **`ShellPolicy`.** Pass it to `ShizukuTools.all(context, policy)`, `RootTools.all(context, policy)` or `ShellTools.all(context, backend, policy)`. It denies specific setting keys (in every namespace, case-insensitive) and specific permissions for `grant_permission` (exact match). `revoke_permission` is not affected. The default, `ShellPolicy.RECOMMENDED`, denies the keys in `RECOMMENDED_DENIED_SETTING_KEYS` and the permissions in `RECOMMENDED_DENIED_PERMISSIONS`: accessibility, notification-listener, IME, ADB, developer-options and package-verifier settings, plus `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `DUMP`, `PACKAGE_USAGE_STATS`, `INTERACT_ACROSS_USERS`, `SET_PROCESS_LIMIT` and `CHANGE_CONFIGURATION`. Pass `ShellPolicy.PERMISSIVE` to deny nothing. The policy does not cover the package-manager tools.
- **Register less.** Filter the list from `all()` by name, gate tools at runtime with `DroidMcp.setToolEnabled` / `setDisabledTools`, or serve over HTTP with `readOnly = true`.
- **`run_shell` allowlist.** `run_shell` refuses every command until the host calls `ShellAllowlist.set(setOf(...))`. The allowlist is process-global.
  - Entries match the request's argv token by token, as a prefix. `"pm list"` matches `pm list packages`, but not `pmx` or `pm listx`.
  - Arguments after the matched prefix are not checked, so allowlist the narrowest prefix that works.
  - `set` throws `IllegalArgumentException` for blank entries and for entries whose first token is a shell, interpreter or exec wrapper (`ShellAllowlist.FORBIDDEN_LEADING_COMMANDS`: `sh`, `toybox`, `busybox`, `su`, `app_process`, `env`, `xargs`, `timeout`, `awk`, `python` and others). The previous allowlist is kept.
  - Commands that can start other commands from their arguments (`find -exec`, for example) are not blocked. Review each entry with that in mind.
  - In argv form, `command` must be a single token.
  - The string form splits on whitespace and ignores quotes.

## Shizuku backend limits

`ShizukuShellBackend(execTimeoutMs = 30_000, maxOutputBytes = 4 MiB, maxBinaryOutputBytes = 64 MiB)`:

- Each call has a 30 s wall-clock limit. On top of that, the core `ToolRegistry` applies its own per-tool timeout (default 5 min, set with `Builder.toolTimeout()`).
- stdout and stderr are each capped at 4 MiB. `capture_screen_quiet` gets 64 MiB of stdout.
- The child's stdin is closed at spawn.
- On timeout, cancellation or an exceeded cap, the remote process is destroyed and its pipes are closed.
  - A timeout returns `shell_spawn_failed`.
  - An exceeded cap returns the output captured so far with exit code `-1`. `run_shell` reports this as `stdout_truncated` / `stderr_truncated`; the other tools treat it as a failed command.

`ShizukuTools` uses a default-configured backend. To change the limits, build `ShellTools.all(context, ShizukuShellBackend(execTimeoutMs = ...), policy)` yourself.

**R8.** The backend calls Shizuku's private `Shizuku.newProcess` through reflection. The module ships `consumer-rules.pro` to keep that method and `ShizukuRemoteProcess`, so minified host builds need no extra rules.
