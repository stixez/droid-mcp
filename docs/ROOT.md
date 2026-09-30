# Activating Root for droid-mcp

`droid-mcp-root` (Tier 5) gives an LLM `root`-UID access to the device — the same 17 shell tools as `droid-mcp-shizuku`, but routed through `su` instead of the Shizuku binder, with broader capability: writes to `/system`, freezing apps via `pm hide`, reading `/data/data/<pkg>`, etc.

Backed by [libsu](https://github.com/topjohnwu/libsu) from the Magisk author. Compatible with any superuser manager: Magisk, KernelSU, SuperSU, APatch.

## 1. Root your device

This is a one-time setup outside droid-mcp's scope. Common paths:

- **Magisk** (most common): unlock bootloader → patch boot image → flash via fastboot. See [topjohnwu/Magisk releases](https://github.com/topjohnwu/Magisk/releases).
- **KernelSU**: kernel-level alternative for some devices. See [tiann/KernelSU](https://github.com/tiann/KernelSU).
- **SuperSU**: legacy, mostly Android 9 and older.
- **APatch**: Magisk-compatible patcher.

All four expose the same `su` binary contract that libsu uses; the SDK doesn't care which manager you run.

## 2. Grant the host app root access

On the first root shell creation (`RootTools.requestAccess()`, i.e. `Shell.getShell { }`), the superuser manager surfaces a prompt asking whether to grant root to the host app. Tap **Allow** (or "Grant" / "Forever").

droid-mcp's `RootTools.requestAccess(onResult)` triggers this prompt asynchronously without blocking the UI. The callback fires once libsu has the shell ready, with `true` if the resulting shell is a root shell. The sample app wires it to the **Grant Access** chip on the "Root" tool category: grant arrives → callback fires → `MainViewModel.initialize()` re-runs → Root-backed tools appear in the registry without an app restart.

After grant, libsu caches the root shell for the process lifetime; subsequent commands don't re-prompt.

### Host responsibility — do not override libsu's default builder to a non-root config

droid-mcp's root flow relies on libsu's *default* `Shell.Builder` (which prefers root). If your host has called `Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_NON_ROOT_SHELL))` (or equivalent) before any droid-mcp code runs, libsu silently uses a non-root shell, `Shell.isAppGrantedRoot()` returns `false` (or the shell starts without firing the su prompt), and every root tool errors with `shell_unavailable`. droid-mcp doesn't override the default builder — that's the host's responsibility to leave alone.

## 3. Verify

`RootTools.isRootAvailable()` returns true only when libsu reports `Shell.isAppGrantedRoot() == true`. Equivalently, `RootTools.permissionStatus(context)` returns `PermissionStatus.Granted`.

If any tool returns `shell_unavailable: Root (libsu)`, libsu doesn't think root is reachable. Common causes:
- The device isn't rooted.
- The user denied the prompt — they can re-grant in their superuser manager's app-permissions screen.
- `Shell.isAppGrantedRoot()` returned `null` because `requestAccess()` was never called.

## 4. Root vs Shizuku: which to use

Both expose the same 17 tools (same names, same parameters, same output shape). Pick based on threat model + UX:

| Aspect | Shizuku | Root |
|---|---|---|
| Activation | Install Shizuku, activate via wireless debugging or ADB | Root the device (bootloader unlock, etc.) |
| Survives reboot | ❌ Re-activate via wireless debugging on every reboot | ✅ Magisk/KernelSU re-grant root on boot |
| Privilege level | `shell` UID — same as `adb shell` | `root` UID — system writes, `pm hide`, `/data/data/<pkg>` |
| User-friction first run | Medium (pair + activate Shizuku) | High (must already be rooted) |
| Security boundary | Per-app grant managed by Shizuku | Per-app grant managed by superuser manager |
| Best for | Power users on stock Android | Already-rooted users; capabilities `shell` UID can't provide |

For most consumer LLM-phone-agent use cases, **prefer Shizuku** — it's accessible to non-rooted users and covers everything shell-core's tools need. Reach for root only when a specific capability (`/system` write, `pm hide`, deep `/data` access) demands it.

## 5. Use both — root preferred, Shizuku fallback

Hosts that want "use whichever's available, prefer root" can write a tiny dispatch wrapper:

```kotlin
val backend: ShellBackend = when {
    RootShellBackend().isAvailable() -> RootShellBackend()
    ShizukuShellBackend().isAvailable() -> ShizukuShellBackend()
    else -> NoOpShellBackend  // every tool errors with shell_unavailable
}
val tools = ShellTools.all(context, backend)
mcp.addTools(tools)
```

This keeps tool names stable (the LLM sees the same `force_stop_app` etc.) and lets the host decide the privilege ladder.

The sample app uses a simpler "last-write-wins" pattern: registers Shizuku first, then root if root happens to be available. The shell tools get overwritten in the registry to point at the root backend when both are present. Document the behaviour in your host so users know which backend is actually running their requests.

## 6. Security notes

Granting root to a host app is **strictly more powerful** than Shizuku — that app can now do everything the device can do. Treat as you would `su` access from a terminal: only enable for apps you trust deeply. droid-mcp's MCP server bearer-auth still gates the LLM's tool calls, but a malicious / compromised tool gets `root`-UID range once it crosses that boundary.

**What the dedicated tools can do — they are not narrow.** Even with `run_shell` disabled, the typed tools are enough to take over the device if the model is steered by a malicious prompt (e.g. via a notification, web page or message it reads):

- `put_secure_setting` / `put_global_setting` / `put_system_setting` write *any* key: enabling an accessibility service or notification listener (`enabled_accessibility_services`, `enabled_notification_listeners`), switching the default keyboard (`default_input_method`), turning on ADB (`adb_enabled`, `adb_wifi_enabled`), disabling package verification, etc.
- `grant_permission` grants any runtime permission the target app declares — and `development`-protection permissions such as `WRITE_SECURE_SETTINGS`.
- `install_apk` installs any readable APK silently (the path must be absolute and end in `.apk`, but there is no storage sandbox); `uninstall_app` / `clear_app_data` / `disable_app` are destructive.

**Host denylist.** Pass a `ShellPolicy` when registering to block specific setting keys and grantable permissions — denied calls return `denied_by_policy` without spawning anything. The default (`ShellPolicy.PERMISSIVE`) blocks nothing; `ShellPolicy.RECOMMENDED` blocks the keys/permissions above:

```kotlin
RootTools.all(context, ShellPolicy.RECOMMENDED)
```

Only register the tools you actually need (filter the returned list by name), and prefer `readOnly` servers or per-tool gating for anything exposed to untrusted input.

**`run_shell` allowlist.** `run_shell` is default-deny. `ShellAllowlist.set(...)` entries are matched token-by-token against the request's leading argv (`"pm list"` matches `pm list packages`, not `pmx` or `pm listx`); arguments after the matched prefix are unrestricted, so allowlist the narrowest prefix that works. Entries starting with an interpreter or exec-wrapper (`sh`, `bash`, `toybox`, `busybox`, `su`, `app_process`, `env`, `xargs`, `nohup`, `timeout`, `awk`, `python`, …) are **rejected** with `IllegalArgumentException`, because allowlisting one would let the model run anything as the `root` UID. Commands that can spawn subprocesses from their arguments (`find -exec`, …) can't all be enumerated — review each entry with that in mind.

**Timeouts and output caps.** Each call runs in its own freshly built `su` shell (never libsu's shared main shell), with stdin redirected from `/dev/null`, so a command that reads stdin, changes directory/environment or exits can't poison later calls. Calls are limited to 30 s (configurable via `RootShellBackend(execTimeoutMs = ...)`) and ~4 MiB per stream (64 MiB stdout for `capture_screen_quiet`, whose temp file under `/data/local/tmp` is created with `umask 077` and always deleted). On timeout, caller cancellation or an exceeded cap the dedicated shell is closed, killing the command. A timeout reports `shell_spawn_failed`; an exceeded cap returns the captured prefix with `stdout_truncated`/`stderr_truncated` and `exit_code: -1`. stderr is captured separately. Some superuser managers show a toast or log entry per `su` session — expect one per tool call.

**libsu license:** libsu is Apache 2.0. droid-mcp depends on the `:core` and `:io` artifacts only; we don't bundle Magisk binaries, Magisk app code, or any other content from the Magisk repository. The libsu API surface we use is public (`Shell.Builder`, `Shell.newJob()`, `Shell.isAppGrantedRoot()`, `Shell.getShell {}`, `SuFile` / `SuFileInputStream`).
