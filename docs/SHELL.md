# Shell tools: Shizuku and root

`droid-mcp-shell-core` has 17 privileged tools: install and uninstall apps, grant permissions, write system settings, take a silent screenshot, and run allowlisted commands. Two opt-in modules run them, with the same tool names and results:

| | `droid-mcp-shizuku` | `droid-mcp-root` |
|---|---|---|
| Runs as | `shell` UID (like `adb shell`) via [Shizuku](https://github.com/RikkaApps/Shizuku) | `root` via `su` and [libsu](https://github.com/topjohnwu/libsu) |
| Device needs | Shizuku app, started via wireless debugging or ADB | Root (Magisk, KernelSU) |
| After reboot | Restart Shizuku | Keeps working |

Root only adds what you allowlist for `run_shell` (writing `/system`, reading other apps' data). If you don't need that, use Shizuku.

> [!IMPORTANT]
> Anyone who can call these tools gets that UID. Read [Safety](#safety) first.

## Setup: Shizuku

1. Add `implementation("com.github.stixez.droid-mcp:droid-mcp-shizuku:0.14.0")`.
2. Declare the provider in your manifest (its authority must use your application ID):

   ```xml
   <provider
       android:name="rikka.shizuku.ShizukuProvider"
       android:authorities="${applicationId}.shizuku"
       android:multiprocess="false"
       android:enabled="true"
       android:exported="true"
       android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
   ```

3. The user installs Shizuku and starts it; the Shizuku app shows how.
4. Ask for permission. The answer arrives only through a listener:

   ```kotlin
   Shizuku.addRequestPermissionResultListener { code, result ->
       if (result == PackageManager.PERMISSION_GRANTED) { /* register the tools */ }
   }
   when {
       ShizukuTools.isShizukuReady() -> Unit
       Shizuku.pingBinder() -> ShizukuTools.requestPermission(REQUEST_CODE)
       else -> startActivity(ShizukuTools.installOrOpenIntent(context)) // open or install Shizuku
   }
   ```

5. Register: `DroidMcp.builder().addTools(ShizukuTools.all(context))`.

R8 rules ship with the module.

## Setup: root

1. Add `implementation("com.github.stixez.droid-mcp:droid-mcp-root:0.14.0")` (libsu comes from JitPack).
2. Ask for root, then register:

   ```kotlin
   RootTools.requestAccess { granted ->  // runs on a worker thread
       if (granted) { /* register RootTools.all(context) */ }
   }
   ```

Until `requestAccess` has run, root reads as unavailable and every tool returns `shell_unavailable`.

## One backend at a time

Both modules register the same names, so register one. To prefer root and fall back to Shizuku:

```kotlin
val backend = listOf(RootShellBackend(), ShizukuShellBackend()).firstOrNull { it.isAvailable() } ?: NoOpShellBackend
builder.addTools(ShellTools.all(context, backend, ShellPolicy.RECOMMENDED))
```

The tools are always registered and check the backend on each call. `ShizukuTools.permissionStatus(context)` and `RootTools.permissionStatus(context)` tell you what's missing.

## Tools

The 17 tools and their parameters are listed in [TOOLS.md](TOOLS.md#shell-shizuku-or-root). Only `get_top_window`, `list_app_permissions` and `capture_screen_quiet` are read-only; all others count as destructive, so `confirmToolCalls` asks before each.

Errors:

| Code | Meaning |
|---|---|
| `shell_unavailable` | Backend not running |
| `shell_permission_denied` | Backend running, access not granted |
| `shell_spawn_failed` | Couldn't start the process, or it timed out |
| `denied_by_policy` | Blocked by `ShellPolicy` |
| `run_shell_not_enabled` | Command not on the allowlist |
| `invalid_*` | Bad input |

Each tool also has its own failure code, such as `install_failed`.

## Safety

The token is the only thing in front of these tools. Without `run_shell` they can still take over the device: the `put_*_setting` tools can turn on an accessibility service or ADB, `grant_permission` can grant `WRITE_SECURE_SETTINGS`, and `install_apk` installs silently. A prompt-injected model could try any of this. Your controls, none of which the model can change:

**`ShellPolicy`**, passed to `all(context, policy)`:

- `RECOMMENDED` (default) blocks setting keys for accessibility, notification access, keyboards, ADB, developer options, unknown sources and package verification. It also blocks granting `WRITE_SECURE_SETTINGS`, `READ_LOGS`, `DUMP`, `PACKAGE_USAGE_STATS`, `INTERACT_ACROSS_USERS`, `SET_PROCESS_LIMIT` and `CHANGE_CONFIGURATION`.
- `PERMISSIVE` blocks nothing.
- `ShellPolicy(deniedSettingKeys, deniedPermissions)` takes your own lists; start from the `RECOMMENDED_*` sets.

**Register less:** filter `all()` by name, use `setDisabledTools`, or serve with `readOnly = true`.

**`run_shell` allowlist:** nothing runs until you call `ShellAllowlist.set(setOf("pm list packages", "dumpsys battery"))`.

- Entries match the command's leading words, so `"pm list"` allows `pm list packages` but not `pmx`.
- Later arguments aren't checked, so allowlist the narrowest prefix.
- Entries starting with a shell or interpreter (`sh`, `toybox`, `su`, `env`, `python` and others) are rejected.
- Commands that run other commands (`find -exec`) are not blocked; review entries with that in mind.

**`install_apk`** accepts only an absolute `.apk` path without `..` or control characters. Any APK the backend can read can be installed.

## Limits

- **Time:** each call is killed after 30 s and returns `shell_spawn_failed`.
- **Output:** stdout and stderr are each capped at 4 MiB; `capture_screen_quiet` gets 64 MiB. Hitting the cap kills the process.
- **Input:** stdin is closed, so a command that waits for input ends instead of hanging.
- **Changing the limits:** build the backend yourself, e.g. `ShellTools.all(context, ShizukuShellBackend(execTimeoutMs = 10_000), policy)`.
- **Root:** each call opens its own `su` shell, so one call can't affect the next. Some superuser managers show a toast per call.
