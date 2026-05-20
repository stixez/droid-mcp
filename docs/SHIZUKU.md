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
- **API version:** droid-mcp's Shizuku integration is pinned to API v13.x. The Shizuku app and the API library are usually decoupled; any recent Shizuku app build is compatible.

## 6. Security notes

Granting Shizuku to a host app is a meaningful trust extension — that app can now run anything `adb shell` can. Treat the grant as you would `adb shell` access: only enable it for apps you trust. droid-mcp uses Shizuku to mediate LLM tool calls; the host app's MCP server bearer-auth setup is still the boundary the LLM has to clear, but a malicious / compromised tool call gets `shell`-UID range once it crosses that boundary.

The default-deny `ShellAllowlist` on the `run_shell` tool prevents arbitrary command execution by the LLM; specific tools like `force_stop_app` / `put_secure_setting` are narrow-scope by construction. Don't broaden the allowlist beyond what your app actually needs.
