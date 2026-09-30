package io.droidmcp.root

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import io.droidmcp.shell.ShellBackend
import io.droidmcp.shell.ShellException
import io.droidmcp.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.AbstractList
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * [ShellBackend] backed by libsu — runs commands as the `root` UID. Strictly
 * more powerful than Shizuku's `ShizukuShellBackend`: writes to
 * `/system`, freezes apps via `pm hide`, reads `/data/data/<pkg>`, etc.
 *
 * **One shell per call.** Each [exec] / [execBinary] builds a dedicated `su`
 * shell via `Shell.Builder.create().build()` and closes it afterwards, instead
 * of using libsu's process-global main shell. A command that reads stdin, `cd`s,
 * exports variables, `exec`s or `exit`s therefore can't poison later calls (or the
 * host's own libsu usage). The command's stdin is additionally redirected from
 * `/dev/null`. Building a shell costs one `su` round-trip (typically a few ms;
 * some superuser managers show a toast / log entry per session).
 *
 * **Bounds.** Each call is limited to [execTimeoutMs] and to [maxOutputBytes] of
 * captured stdout / stderr each. On timeout or caller cancellation the dedicated
 * shell is closed (killing the `su` process and its child); a timeout surfaces
 * as [ShellException.SpawnFailed]. When a stream exceeds its cap the shell is
 * killed and the captured prefix is returned with [ShellResult.outputTruncated]
 * set and `exitCode = -1`.
 *
 * **Output model.** libsu's [Shell.Result.out] is `List<String>` (UTF-8
 * line-decoded). [exec] joins those lines back into a byte stream via UTF-8
 * re-encoding which is **lossy for binary stdout** (the PNG signature
 * `89 50 4E 47 ...` is invalid UTF-8, so the round-trip mangles it). Tools
 * whose stdout is raw bytes — `screencap -p`, anything streaming
 * non-UTF-8 — must call [execBinary], which uses a temp-file roundtrip
 * via [SuFileInputStream] to read raw bytes. stderr is collected separately
 * (`Job.to(out, err)`), so [ShellResult.stderr] is populated.
 *
 * **Host responsibility — do not override libsu's default builder to a
 * non-root config.** [requestAccess][io.droidmcp.root.RootTools.requestAccess]
 * calls [Shell.getShell] which uses whatever default builder is configured.
 * If your host has called `Shell.setDefaultBuilder(...)` with non-root flags
 * before any droid-mcp code runs, the prompt will never fire and root tools
 * will silently fail with `shell_unavailable`. libsu's untouched default
 * prefers root, which is the configuration droid-mcp expects. (The per-call
 * shells use a fresh default `Shell.Builder`, unaffected by the host's.)
 *
 * **Command parsing.** libsu writes each command to the stdin of the shell
 * process — it is NOT a `sh -c <string>` wrap. The shell still parses each line
 * per `sh` syntax, so argv quoting via [ShellUtils.escapedString] is applied to
 * every argument.
 *
 * @param execTimeoutMs wall-clock limit per call. The core `ToolRegistry` applies
 *   its own (longer) per-tool timeout on top.
 * @param maxOutputBytes per-stream capture cap (approximate: counted as UTF-16
 *   chars + 1 per line, as libsu hands us decoded lines).
 * @param maxBinaryOutputBytes stdout byte cap for [execBinary].
 */
class RootShellBackend(
    private val execTimeoutMs: Long = DEFAULT_EXEC_TIMEOUT_MS,
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val maxBinaryOutputBytes: Int = DEFAULT_MAX_BINARY_OUTPUT_BYTES,
) : ShellBackend {

    override val name: String = "Root (libsu)"

    override fun isAvailable(): Boolean = Shell.isAppGrantedRoot() == true

    override fun availabilityProblem(): ShellException? = when (Shell.isAppGrantedRoot()) {
        null -> ShellException.NotAvailable(
            "Root access not yet checked. Call RootTools.requestAccess() from an Activity to trigger the su prompt."
        )
        false -> ShellException.PermissionDenied(
            "Root not granted: the device isn't rooted, or the superuser manager denied this app."
        )
        true -> null
    }

    override suspend fun exec(command: String, args: List<String>): ShellResult = withContext(Dispatchers.IO) {
        ensureRootGranted()
        val run = runInDedicatedShell(buildCommandLine(command, args) + " </dev/null")
        ShellResult(
            exitCode = run.exitCode,
            stdoutBytes = run.out.joinToString("\n").toByteArray(Charsets.UTF_8),
            stderr = run.err.joinToString("\n"),
            outputTruncated = run.truncated,
        )
    }

    /**
     * Binary-safe path: writes stdout to a tempfile under `/data/local/tmp`
     * (created under `umask 077`, so it is root-only `0600` from the start), then
     * reads it raw via [SuFileInputStream], capped at [maxBinaryOutputBytes].
     * Used by `capture_screen_quiet` for PNG bytes.
     */
    override suspend fun execBinary(command: String, args: List<String>): ShellResult = withContext(Dispatchers.IO) {
        ensureRootGranted()

        val tempPath = "/data/local/tmp/droidmcp-${UUID.randomUUID()}"
        // The umask only affects this throwaway shell — it is closed after the call.
        val commandLine = "umask 077; " + buildCommandLine(command, args) +
            " </dev/null > " + ShellUtils.escapedString(tempPath)

        try {
            val run = runInDedicatedShell(commandLine)
            var truncated = run.truncated
            val stdoutBytes: ByteArray = if (run.exitCode == 0) {
                SuFileInputStream.open(tempPath).use { input ->
                    val output = ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        val room = maxBinaryOutputBytes - output.size()
                        if (n > room) {
                            if (room > 0) output.write(buf, 0, room)
                            truncated = true
                            break
                        }
                        output.write(buf, 0, n)
                    }
                    output.toByteArray()
                }
            } else {
                ByteArray(0)
            }
            ShellResult(
                exitCode = run.exitCode,
                stdoutBytes = stdoutBytes,
                stderr = run.err.joinToString("\n"),
                outputTruncated = truncated,
            )
        } finally {
            // Also covers timeout / cancellation / exceptions: the redirect may already
            // have created the file, so never orphan it in /data/local/tmp.
            runCatching { SuFile(tempPath).delete() }
        }
    }

    private class RunResult(val exitCode: Int, val out: List<String>, val err: List<String>, val truncated: Boolean)

    /**
     * Run [commandLine] in a freshly built root shell, bounded by [execTimeoutMs] and
     * [maxOutputBytes], honouring coroutine cancellation. The shell is always closed.
     */
    private suspend fun runInDedicatedShell(commandLine: String): RunResult {
        val shell = try {
            Shell.Builder.create()
                .setTimeout(SHELL_BUILD_TIMEOUT_S)
                .build()
        } catch (t: Throwable) {
            throw ShellException.NotAvailable("could not start a su shell: ${t.message ?: t::class.java.simpleName}")
        }
        try {
            if (!shell.isRoot) {
                throw ShellException.PermissionDenied("su did not yield a root shell (root revoked in the superuser manager?)")
            }
            val out = CappedLineList(maxOutputBytes)
            val err = CappedLineList(maxOutputBytes)
            val future = try {
                shell.newJob().add(commandLine).to(out, err).enqueue()
            } catch (t: Throwable) {
                throw ShellException.SpawnFailed(t.message ?: t::class.java.simpleName)
            }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(execTimeoutMs)
            while (true) {
                // Cancellation (incl. the core ToolRegistry timeout) unwinds through the
                // finally below, which closes the shell and kills the command.
                currentCoroutineContext().ensureActive()
                if (out.exceeded || err.exceeded) {
                    return RunResult(-1, out.snapshot(), err.snapshot(), truncated = true)
                }
                try {
                    val result = future.get(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    return RunResult(result.code, out.snapshot(), err.snapshot(), truncated = false)
                } catch (_: TimeoutException) {
                    // fall through to the deadline check
                } catch (e: ExecutionException) {
                    val cause = e.cause ?: e
                    throw ShellException.SpawnFailed(cause.message ?: cause::class.java.simpleName)
                } catch (e: InterruptedException) {
                    throw ShellException.SpawnFailed("interrupted while waiting for the root shell")
                }
                if (System.nanoTime() - deadline >= 0) {
                    throw ShellException.SpawnFailed("Command timed out after ${execTimeoutMs}ms without exiting")
                }
            }
        } finally {
            // Kills the su process (and with it any still-running command) and shuts down
            // the shell's executor. Never touches libsu's main shell.
            runCatching { shell.close() }
        }
    }

    /**
     * Line sink handed to libsu's stream gobblers. Stops retaining lines once
     * [capChars] (chars + 1 per newline) is reached and flags [exceeded]; the
     * wait loop then kills the shell. Synchronized because the gobbler thread adds
     * while the caller may snapshot on the truncation path.
     */
    private class CappedLineList(private val capChars: Int) : AbstractList<String>() {
        private val lines = ArrayList<String>()
        private var chars = 0L

        @Volatile
        var exceeded: Boolean = false
            private set

        @Synchronized
        override fun add(element: String): Boolean {
            if (exceeded) return true
            val cost = element.length + 1L
            if (chars + cost > capChars) {
                val room = (capChars - chars).toInt()
                if (room > 0) lines.add(element.take(room))
                exceeded = true
                return true
            }
            chars += cost
            return lines.add(element)
        }

        @Synchronized
        override fun get(index: Int): String = lines[index]

        override val size: Int
            @Synchronized get() = lines.size

        @Synchronized
        fun snapshot(): List<String> = ArrayList(lines)
    }

    private fun ensureRootGranted() {
        availabilityProblem()?.let { throw it }
    }

    private fun buildCommandLine(command: String, args: List<String>): String = buildString {
        append(ShellUtils.escapedString(command))
        args.forEach { arg ->
            append(' ')
            append(ShellUtils.escapedString(arg))
        }
    }

    companion object {
        /** Default wall-clock limit per call. */
        const val DEFAULT_EXEC_TIMEOUT_MS = 30_000L

        /** Default per-stream capture cap for text output (~4 MiB). */
        const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024 * 1024

        /** Default stdout cap for [execBinary] (64 MiB — fits any screencap PNG). */
        const val DEFAULT_MAX_BINARY_OUTPUT_BYTES = 64 * 1024 * 1024

        /** Seconds libsu may wait for `su` to hand back a shell. */
        private const val SHELL_BUILD_TIMEOUT_S = 10L

        private const val POLL_INTERVAL_MS = 200L
    }
}
