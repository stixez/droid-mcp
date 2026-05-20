package io.droidmcp.shell

/**
 * Abstraction over a privileged shell-command pipeline.
 *
 * Two concrete implementations are planned:
 *  - `ShizukuShellBackend` (in `:droid-mcp-shizuku`, ships 0.8.0) — wraps
 *    `Shizuku.newProcess` for `shell`-UID execution without root.
 *  - `RootShellBackend` (in `:droid-mcp-root`, ships 0.9.0) — wraps `libsu` for
 *    `root`-UID execution. Same surface, broader capabilities (writes to
 *    `/system`, etc.).
 *
 * Tools in `:droid-mcp-shell-core` are parameterised over [ShellBackend] so
 * the LLM-facing surface is identical across backends; the host app picks
 * which one (or both) to register at startup.
 */
interface ShellBackend {

    /**
     * True when the backend is currently reachable — Shizuku binder pingable
     * with permission granted, or `su` available, depending on the impl.
     * Tools call this for fast-fail before constructing a command.
     */
    fun isAvailable(): Boolean

    /**
     * Run [command] with [args] as a shell process. Returns stdout/stderr/exit.
     * Implementations should NOT throw on non-zero exit codes — surface them
     * via [ShellResult.exitCode] so tools can decide whether that's an error.
     *
     * Throws [ShellException] only on infrastructure failures (binder down,
     * permission denied, process spawn failed). Tools translate that into a
     * `shell_unavailable: <reason>` MCP error.
     *
     * **Output format is implementation-dependent for binary commands.**
     * The Shizuku backend returns raw bytes (it reads `process.inputStream`
     * directly). The libsu backend's `Shell.Result.out` is line-decoded
     * UTF-8 — binary stdout (`screencap -p`, etc.) is corrupted through
     * this path. Tools that produce binary stdout should call [execBinary]
     * instead.
     */
    @Throws(ShellException::class)
    suspend fun exec(command: String, args: List<String> = emptyList()): ShellResult

    /**
     * Run [command] with [args] and return raw stdout bytes. Defaults to
     * [exec] — backends whose [exec] is already binary-safe (Shizuku) don't
     * need to override. Backends with a text-only output API (libsu) MUST
     * override with a binary-safe path (e.g. temp-file roundtrip).
     */
    @Throws(ShellException::class)
    suspend fun execBinary(command: String, args: List<String> = emptyList()): ShellResult =
        exec(command, args)

    /**
     * One-line description of what this backend is, surfaced in tool errors
     * (e.g. `"Shizuku"`, `"libsu (root)"`). Helps the LLM disambiguate when
     * one tool errors with "shell_unavailable" and the host registered
     * multiple backends.
     */
    val name: String
}

/**
 * Result of a shell invocation. `exitCode == 0` is the success convention;
 * tools test it explicitly because some commands return non-zero on success
 * (e.g. `pm grant` for an unknown permission).
 *
 * [stdoutBytes] is the raw byte stream — needed for binary commands like
 * `screencap -p` which emit PNG bytes. [stdout] is the UTF-8 decode of those
 * bytes for text use. [stderr] is always text.
 */
data class ShellResult(
    val exitCode: Int,
    val stdoutBytes: ByteArray,
    val stderr: String,
) {
    val stdout: String by lazy { stdoutBytes.toString(Charsets.UTF_8) }
    val isSuccess: Boolean get() = exitCode == 0
    val output: String get() = if (stdout.isNotEmpty()) stdout else stderr

    companion object {
        fun ofText(exitCode: Int, stdout: String, stderr: String): ShellResult =
            ShellResult(exitCode, stdout.toByteArray(Charsets.UTF_8), stderr)
    }

    // data-class equals/hashCode would include the byte array by reference;
    // override so two results with the same bytes compare equal. Tools rarely
    // rely on this but tests do.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ShellResult) return false
        return exitCode == other.exitCode &&
            stderr == other.stderr &&
            stdoutBytes.contentEquals(other.stdoutBytes)
    }

    override fun hashCode(): Int {
        var result = exitCode
        result = 31 * result + stdoutBytes.contentHashCode()
        result = 31 * result + stderr.hashCode()
        return result
    }
}

/**
 * Infrastructure failure — the backend itself couldn't run a process. Distinct
 * from a non-zero exit code returned by the spawned process.
 */
sealed class ShellException(message: String) : Exception(message) {
    class NotAvailable(reason: String) : ShellException("shell backend not available: $reason")
    class PermissionDenied(reason: String) : ShellException("shell permission denied: $reason")
    class SpawnFailed(reason: String) : ShellException("shell spawn failed: $reason")
}
