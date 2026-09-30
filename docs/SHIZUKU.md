# Activating Shizuku for droid-mcp

`droid-mcp-shizuku` (Tier 4) gives an LLM `shell`-UID access to the device — silent app install/uninstall, force-stop, secure-settings writes, dumpsys, screencap without consent prompt, etc. — without requiring root.

Shizuku is a third-party app that runs a privileged service the host app talks to over Binder. The user activates Shizuku once via wireless debugging or ADB; from then on, droid-mcp tools that depend on the shell backend just work.

**Alternative:** if your users have rooted devices, [docs/ROOT.md](ROOT.md) covers Tier 5 (libsu) — the same 17 tools, broader privilege, no wireless-debugging activation step. Prefer Shizuku when targeting non-rooted users; prefer root when you need `/system` writes, `pm hide`, or other root-only capabilities. Both tiers share the `ShellBackend` interface so swapping is a one-line registration change.

## 1. Install Shizuku

- **Play Store:** [moe.shizuku.privileged.api](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
- **F-Droid / GitHub:** [github.com/RikkaApps/Shizuku/releases](https://github.com/RikkaApps/Shizuku/releases)

## 2. Activate the Shizuku service

The Shizuku app needs to be running with `shell`-UID privileges. There are two paths:

### Wireless debugging (recommended, Android 11+)

1. Open Settings > System > Developer options > **Wireless debugging**.
2. Toggle Wireless debugging ON. Tap the row, choose **Pair device with pairing code**.
3. In the Shizuku app, tap **Pair device with pairing code** and enter the code shown in Settings.
4. Back in Shizuku, tap **Start** under "Start via Wireless debugging".

The service runs until you reboot or kill the Shizuku app. After reboot, repeat steps 1 + 4 (the pairing usually persists; only the activation needs to be re-triggered).

### ADB (any Android version)

```
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

(The path differs slightly on some devices. Open Shizuku → "Start via ADB" for the exact command for your device.)

## 3. Grant the host app permission

When droid-mcp's `ShizukuTools.requestPermission(requestCode)` is called from an Activity, Shizuku surfaces a system dialog asking the user to allow your app to use Shizuku. Tap **Allow** once; the grant persists for the lifetime of the Shizuku service.

In the sample app this is wired to the **Grant Access** chip on the "Shizuku" tool category. The first tap opens Shizuku if it isn't installed, the second triggers the permission dialog.

### Wire the result listener (so your UI updates on grant)

`Shizuku.requestPermission` doesn't go through `ActivityResultContracts`; instead the result is delivered to a global listener you register. Without one, the user grants permission but your UI has no signal to refresh — they see stale "ungranted" state until the next app launch.

```kotlin
class MainActivity : ComponentActivity() {
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { rc, result ->
        if (rc == SHIZUKU_REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) {
            // Re-evaluate which tools are available now that Shizuku is unlocked.
            viewModelRef?.initialize()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        // ...
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuListener)
        super.onDestroy()
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 0xCAFE
    }
}
```

The sample app uses this pattern verbatim; copy it into your host app's `MainActivity` (or equivalent).

## 4. Verify

`ShizukuTools.isShizukuReady()` returns true when both the binder is reachable and the host has permission. Equivalently, `ShizukuTools.permissionStatus(context)` returns `PermissionStatus.Granted`.

If any tool returns `shell_unavailable: Shizuku`, the binder isn't reachable — Shizuku isn't running. If it returns `shell_permission_denied`, the user hasn't granted access to this host app yet.

## 5. Limitations

- **Survives reboot?** No. Shizuku must be re-activated via wireless debugging or ADB after every reboot. The Shizuku app shows the activation command in a notification once you've activated it once.
- **Sui (root-Shizuku):** users with root can install Sui (Shizuku-as-Magisk-module) and skip the wireless-debugging step. The API is identical; droid-mcp doesn't care which is running.
- **Package visibility:** the module's manifest declares a `<queries>` entry for `moe.shizuku.privileged.api`, so `permissionStatus()` can tell "not installed" from "not activated" on API 30+.
- **API version:** droid-mcp's Shizuku integration is pinned to API v13.x. The Shizuku app and the API library are usually decoupled; any recent Shizuku app build is compatible.

## 6. Security notes

Granting Shizuku to a host app is a meaningful trust extension — that app can now run anything `adb shell` can. Treat the grant as you would `adb shell` access: only enable it for apps you trust. droid-mcp uses Shizuku to mediate LLM tool calls; the host app's MCP server bearer-auth setup is still the boundary the LLM has to clear, but a malicious / compromised tool call gets `shell`-UID range once it crosses that boundary.

**What the dedicated tools can do — they are not narrow.** Even with `run_shell` disabled, the typed tools are enough to take over the device if the model is steered by a malicious prompt (e.g. via a notification, web page or message it reads):

- `put_secure_setting` / `put_global_setting` / `put_system_setting` write *any* key: enabling an accessibility service or notification listener (`enabled_accessibility_services`, `enabled_notification_listeners`), switching the default keyboard (`default_input_method`), turning on ADB (`adb_enabled`, `adb_wifi_enabled`), disabling package verification, etc.
- `grant_permission` grants any runtime permission the target app declares — and `development`-protection permissions such as `WRITE_SECURE_SETTINGS`.
- `install_apk` installs any readable APK silently (the path must be absolute and end in `.apk`, but there is no storage sandbox); `uninstall_app` / `clear_app_data` / `disable_app` are destructive.

**Host denylist.** Pass a `ShellPolicy` when registering to block specific setting keys and grantable permissions — denied calls return `denied_by_policy` without spawning anything. The default (`ShellPolicy.PERMISSIVE`) blocks nothing; `ShellPolicy.RECOMMENDED` blocks the keys/permissions above:

```kotlin
ShizukuTools.all(context, ShellPolicy.RECOMMENDED)
```

Only register the tools you actually need (filter the returned list by name), and prefer `readOnly` servers or per-tool gating for anything exposed to untrusted input.

**`run_shell` allowlist.** `run_shell` is default-deny. `ShellAllowlist.set(...)` entries are matched token-by-token against the request's leading argv (`"pm list"` matches `pm list packages`, not `pmx` or `pm listx`); arguments after the matched prefix are unrestricted, so allowlist the narrowest prefix that works. Entries starting with an interpreter or exec-wrapper (`sh`, `bash`, `toybox`, `busybox`, `su`, `app_process`, `env`, `xargs`, `nohup`, `timeout`, `awk`, `python`, …) are **rejected** with `IllegalArgumentException`, because allowlisting one would let the model run anything as the `shell` UID. Commands that can spawn subprocesses from their arguments (`find -exec`, …) can't all be enumerated — review each entry with that in mind.

**Timeouts and output caps.** Each call runs with a 30 s wall-clock limit (configurable via `ShizukuShellBackend(execTimeoutMs = ...)`) and 4 MiB per stream of captured stdout/stderr (64 MiB stdout for `capture_screen_quiet`). The child's stdin is closed at spawn. On timeout, caller cancellation (the core `ToolRegistry` per-tool timeout included) or an exceeded cap, the remote process is destroyed and its pipes closed. A timeout reports `shell_spawn_failed`; an exceeded cap returns the captured prefix with `stdout_truncated`/`stderr_truncated` and `exit_code: -1`. If the Shizuku service dies mid-call, tools report `shell_unavailable`.

**R8 / ProGuard.** `ShizukuShellBackend` reflectively calls the private `Shizuku.newProcess`. The module ships consumer rules (`consumer-rules.pro`) that keep it, so minified host builds work without extra configuration.
