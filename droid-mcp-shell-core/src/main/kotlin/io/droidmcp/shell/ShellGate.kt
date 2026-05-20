package io.droidmcp.shell

import io.droidmcp.core.ToolResult

/**
 * Shared availability + exception-handling wrapper so individual shell tools
 * stay one-screen long. Wraps a backend call with consistent error envelopes:
 *
 *  - `shell_unavailable: <backend>` when the backend reports not-available
 *  - `shell_permission_denied: <reason>` for [ShellException.PermissionDenied]
 *  - `shell_spawn_failed: <reason>` for [ShellException.SpawnFailed]
 *  - `shell_error: <reason>` for any other [ShellException]
 *
 * Returns the [ShellResult] on success — caller decides whether `exitCode != 0`
 * is an error for their specific verb.
 */
internal suspend inline fun ShellBackend.gatedExec(
    command: String,
    args: List<String> = emptyList(),
    onResult: (ShellResult) -> ToolResult,
): ToolResult {
    if (!isAvailable()) {
        return ToolResult.error("shell_unavailable", name)
    }
    return try {
        onResult(exec(command, args))
    } catch (e: ShellException.NotAvailable) {
        ToolResult.error("shell_unavailable", e.message ?: name)
    } catch (e: ShellException.PermissionDenied) {
        ToolResult.error("shell_permission_denied", e.message)
    } catch (e: ShellException.SpawnFailed) {
        ToolResult.error("shell_spawn_failed", e.message)
    } catch (e: ShellException) {
        ToolResult.error("shell_error", e.message)
    }
}

/**
 * Variant of [gatedExec] that routes through [ShellBackend.execBinary] for
 * tools whose stdout is raw bytes (e.g. `screencap -p` returning PNG).
 * Shizuku's `exec` is already byte-safe so its `execBinary` defaults to
 * `exec`; libsu overrides with a binary-safe temp-file path.
 */
internal suspend inline fun ShellBackend.gatedExecBinary(
    command: String,
    args: List<String> = emptyList(),
    onResult: (ShellResult) -> ToolResult,
): ToolResult {
    if (!isAvailable()) {
        return ToolResult.error("shell_unavailable", name)
    }
    return try {
        onResult(execBinary(command, args))
    } catch (e: ShellException.NotAvailable) {
        ToolResult.error("shell_unavailable", e.message ?: name)
    } catch (e: ShellException.PermissionDenied) {
        ToolResult.error("shell_permission_denied", e.message)
    } catch (e: ShellException.SpawnFailed) {
        ToolResult.error("shell_spawn_failed", e.message)
    } catch (e: ShellException) {
        ToolResult.error("shell_error", e.message)
    }
}
