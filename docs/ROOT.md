# Root backend (Tier 5)

`droid-mcp-root` runs the same 17 shell tools as [`droid-mcp-shizuku`](SHIZUKU.md), with the same names, parameters and results. The difference is that commands go through `su` via [libsu](https://github.com/topjohnwu/libsu) and run as the `root` UID.

For the tool list, error codes and safety controls (`ShellPolicy`, the `run_shell` allowlist, read-only mode), see [SHIZUKU.md](SHIZUKU.md#shell-tools). All of it applies here. A `root` UID can do strictly more than `shell`.

## 1. Add the dependency

```kotlin
implementation("com.github.stixez.droid-mcp:droid-mcp-root:0.11.0")
```

It brings in `droid-mcp-shell-core` and libsu `com.github.topjohnwu.libsu:core` + `:io` 6.0.0. libsu is served from JitPack, so keep `maven { url = uri("https://jitpack.io") }` in your repositories. The module declares no Android permissions and is not part of `droid-mcp-all`.

## 2. Root the device

The device needs a superuser manager that provides `su`, such as Magisk or KernelSU. Setting one up is outside droid-mcp's scope.

## 3. Request root and register the tools

```kotlin
RootTools.requestAccess { granted ->
    // Runs on libsu's worker thread: switch to the main thread before touching UI.
    if (granted) runOnUiThread { /* rebuild or refresh your tool registration */ }
}

val mcp = DroidMcp.builder()
    .addTools(RootTools.all(context))   // ShellPolicy.RECOMMENDED by default
    // ...
    .build()
```

- `requestAccess` calls `Shell.getShell { }` without blocking, which makes the superuser manager show its prompt. The callback receives `true` when the resulting shell is a root shell. You can call it more than once.
- Until `requestAccess` has run, libsu has not checked for root. In that state `isRootAvailable()` is `false` and every tool returns `shell_unavailable`.
- The sample app wires this to the **Grant Access** chip on the "Root" category and re-runs `MainViewModel.initialize()` when root is granted.

**Leave libsu's default builder alone.** `requestAccess` uses libsu's default `Shell.Builder`. If your host calls `Shell.setDefaultBuilder(...)` with non-root flags first, the prompt never appears and the tools stay unavailable.

## 4. Check status

- `RootTools.isRootAvailable()` returns `true` only when `Shell.isAppGrantedRoot() == true`.
- `RootTools.permissionStatus(context)` returns `Granted`, or `NotGranted` for one of two cases: "not checked yet" (`requestAccess` was never called) or "denied" (the grant can be changed in the superuser manager).

If `requestAccess` was never called, tools return `shell_unavailable`. If the device isn't rooted or the prompt was denied, they return `shell_permission_denied`.

## Root backend limits

`RootShellBackend(execTimeoutMs = 30_000, maxOutputBytes = 4 MiB, maxBinaryOutputBytes = 64 MiB)`:

- **One `su` shell per call.** Each call builds its own shell and closes it afterwards. It never uses libsu's shared main shell. stdin is redirected from `/dev/null`. A command that reads stdin, changes directory or environment, or exits cannot affect later calls. Some superuser managers log an entry or show a toast for each `su` session, so expect one per tool call.
- **Time limit.** Each call is limited to 30 s. The core `ToolRegistry` per-tool timeout also applies. On timeout or cancellation, the shell is closed, which kills the command. A timeout returns `shell_spawn_failed`.
- **Output caps.** stdout and stderr are each capped at about 4 MiB. The count is approximate because libsu delivers decoded lines. When a stream hits its cap, the shell is killed and the call returns the output captured so far with exit code `-1`, the same as the [Shizuku backend](SHIZUKU.md#shizuku-backend-limits).
- **`capture_screen_quiet`.** libsu's text output would corrupt binary data. This tool therefore writes to a temp file in `/data/local/tmp`, created under `umask 077` and always deleted, and reads it back with `SuFileInputStream`. The read is capped at 64 MiB.

`RootTools` uses a default-configured backend. To change the limits, build `ShellTools.all(context, RootShellBackend(execTimeoutMs = ...), policy)` yourself.

## Root or Shizuku

| | Shizuku | Root |
|---|---|---|
| Runs as | `shell` UID | `root` UID |
| Setup | Install Shizuku, start it via wireless debugging or ADB | Rooted device with a superuser manager |
| Grant | Shizuku permission dialog | Superuser manager prompt |

The 17 tools behave the same on both. The extra power of root, such as `pm hide`, writing to `/system` or reading `/data/data/<pkg>`, is reachable only through `run_shell`, and only for commands you allowlist. If you don't need that, prefer Shizuku.

Both providers register the same tool names. The registry keeps the last tool registered under a name, so register one backend, not both. To prefer root and fall back to Shizuku, pick a backend at startup:

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
    else -> NoOpShellBackend // every tool returns shell_unavailable: NoOp
}
val mcp = DroidMcp.builder()
    .addTools(ShellTools.all(context, backend, ShellPolicy.RECOMMENDED))
    .build()
```

The sample app does the same with the providers: `RootTools.all(context)` when root is already granted, otherwise `ShizukuTools.all(context)`.
