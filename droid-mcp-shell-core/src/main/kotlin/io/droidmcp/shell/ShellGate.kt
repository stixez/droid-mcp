package io.droidmcp.shell

import io.droidmcp.core.ToolResult

/**
 * Shared availability + exception-handling wrapper so individual shell tools
 * stay one-screen long. Wraps a backend call with consistent error envelopes:
 *
 *  - `shell_unavailable: <reason>` for [ShellException.NotAvailable], including the
 *    [ShellBackend.availabilityProblem] pre-check (not running / not checked yet)
 *  - `shell_permission_denied: <reason>` for [ShellException.PermissionDenied], including
 *    the pre-check (Shizuku permission or root not granted)
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
    availabilityProblem()?.let { return it.toToolResult(name) }
    return try {
        onResult(exec(command, args))
    } catch (e: ShellException) {
        e.toToolResult(name)
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
    availabilityProblem()?.let { return it.toToolResult(name) }
    return try {
        onResult(execBinary(command, args))
    } catch (e: ShellException) {
        e.toToolResult(name)
    }
}

/** Maps a [ShellException] onto the shared shell error envelope. */
internal fun ShellException.toToolResult(backendName: String): ToolResult = when (this) {
    is ShellException.NotAvailable -> ToolResult.error("shell_unavailable", message ?: backendName)
    is ShellException.PermissionDenied -> ToolResult.error("shell_permission_denied", message)
    is ShellException.SpawnFailed -> ToolResult.error("shell_spawn_failed", message)
}
