package io.droidmcp.shizuku

import android.content.pm.PackageManager
import android.os.RemoteException
import io.droidmcp.shell.ShellBackend
import io.droidmcp.shell.ShellException
import io.droidmcp.shell.ShellResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * [ShellBackend] backed by Shizuku's `newProcess` binder. Runs commands as the
 * `shell` UID — broader than the host app but narrower than root.
 *
 * Activation is the user's responsibility: install the Shizuku app, activate
 * it via wireless debugging (Android 11+) or ADB, grant the permission. The
 * backend reports `isAvailable() == false` until both the binder is reachable
 * and the host has permission. See `docs/SHIZUKU.md`.
 *
 * **Bounds.** Each [exec] is limited to [execTimeoutMs] of wall-clock time and
 * [maxOutputBytes] of captured stdout / stderr each ([execBinary] uses the larger
 * [maxBinaryOutputBytes] for stdout, since `screencap -p` PNGs are multi-MB). The
 * spawned process's stdin is closed immediately, so a command that reads stdin
 * sees EOF instead of hanging. On timeout, caller cancellation, or an exceeded
 * output cap the remote process is destroyed and its pipes closed. A timeout
 * surfaces as [ShellException.SpawnFailed]; an exceeded cap returns the captured
 * prefix with [ShellResult.outputTruncated] set and `exitCode = -1`.
 *
 * **Binder death.** A `RemoteException` (Shizuku server killed, device
 * re-locked, etc.) from spawn / wait / destroy surfaces as
 * [ShellException.NotAvailable], i.e. `shell_unavailable`.
 *
 * **Implementation note (Shizuku v13):** `Shizuku.newProcess(...)` is private
 * in v13. We reflectively access it because the alternative (a full AIDL
 * UserService pattern) is substantially more code for the same effective
 * behaviour. Migrating to a proper UserService is tracked for a follow-up
 * release; reflection is the documented pragmatic path used by other Shizuku
 * consumers in the v13 era. The module ships consumer ProGuard rules keeping
 * that member so R8-minified hosts keep working.
 *
 * @param execTimeoutMs wall-clock limit per call. Note the core `ToolRegistry`
 *   applies its own (longer) per-tool timeout on top.
 * @param maxOutputBytes per-stream capture cap for [exec] (and stderr of [execBinary]).
 * @param maxBinaryOutputBytes stdout capture cap for [execBinary].
 */
class ShizukuShellBackend(
    private val execTimeoutMs: Long = DEFAULT_EXEC_TIMEOUT_MS,
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
    private val maxBinaryOutputBytes: Int = DEFAULT_MAX_BINARY_OUTPUT_BYTES,
) : ShellBackend {

    override val name: String = "Shizuku"

    override fun isAvailable(): Boolean = availabilityProblem() == null

    override fun availabilityProblem(): ShellException? = runCatching {
        when {
            !Shizuku.pingBinder() -> ShellException.NotAvailable("Shizuku is not running")
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                ShellException.PermissionDenied("Shizuku permission not granted to this app")
            else -> null
        }
    }.getOrElse { ShellException.NotAvailable("Shizuku is not reachable: ${it.message}") }

    override suspend fun exec(command: String, args: List<String>): ShellResult =
        execCapped(command, args, stdoutCap = maxOutputBytes)

    override suspend fun execBinary(command: String, args: List<String>): ShellResult =
        execCapped(command, args, stdoutCap = maxBinaryOutputBytes)

    private suspend fun execCapped(command: String, args: List<String>, stdoutCap: Int): ShellResult = withContext(Dispatchers.IO) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            throw ShellException.NotAvailable("Shizuku binder not reachable; install/activate Shizuku and try again")
        }
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: RuntimeException) {
            throw binderFailureOr(e) { ShellException.NotAvailable("Shizuku permission check failed: ${e.message}") }
        }
        if (!granted) {
            throw ShellException.PermissionDenied("Shizuku permission not granted to this app; call Shizuku.requestPermission() from an Activity")
        }

        val argv = (listOf(command) + args).toTypedArray()
        val process = spawnViaShizuku(argv)
        // Fetch each pipe exactly once: ShizukuRemoteProcess.getErrorStream() makes a
        // fresh binder call (and a new fd) on every invocation, so closing a second
        // handle in teardown would not unblock the drain reading the first.
        val (stdout, stderr) = try {
            // Nothing is ever written to the child's stdin — close it so a command that
            // reads stdin gets EOF rather than blocking until the timeout.
            runCatching { process.outputStream.close() }
            process.inputStream to process.errorStream
        } catch (e: RuntimeException) {
            runCatching { process.destroy() }
            throw binderFailureOr(e) { ShellException.SpawnFailed("failed to open process pipes: ${e.message}") }
        }

        val stdoutBuf = ByteArrayOutputStream()
        val stderrBuf = ByteArrayOutputStream()
        val limitExceeded = AtomicBoolean(false)
        // Set before we close the pipes ourselves, so the IOExceptions that teardown
        // provokes in the drain jobs aren't mistaken for real read failures.
        val tearingDown = AtomicBoolean(false)
        val drainError = AtomicReference<IOException?>(null)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(execTimeoutMs)

        var exitCode = -1
        var timedOut = false
        var drainIncomplete = false
        try {
            // Structured concurrency: coroutineScope joins the drain children before
            // returning. The finally below closes the pipes BEFORE that join, which is
            // what unblocks a drain stuck in read() (e.g. a daemon child inherited the
            // pipe and never closes it).
            coroutineScope {
                val outJob = launch(Dispatchers.IO) {
                    drain(stdout, stdoutBuf, stdoutCap, limitExceeded, tearingDown, drainError)
                }
                val errJob = launch(Dispatchers.IO) {
                    drain(stderr, stderrBuf, maxOutputBytes, limitExceeded, tearingDown, drainError)
                }
                try {
                    // ShizukuRemoteProcess.waitFor() is a blocking binder call that thread
                    // interruption does NOT abort, so it can't be cancelled. Poll with a
                    // short server-side timeout instead, re-checking cancellation, the
                    // deadline and the output cap between polls.
                    while (true) {
                        ensureActive()
                        if (limitExceeded.get()) break
                        if (process.waitForTimeout(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                            exitCode = process.exitValue()
                            break
                        }
                        if (System.nanoTime() - deadline >= 0) {
                            timedOut = true
                            break
                        }
                    }
                    if (!timedOut && !limitExceeded.get()) {
                        // Process exited — give the drains a short grace period to hit EOF.
                        val drained = withTimeoutOrNull(DRAIN_GRACE_MS) {
                            outJob.join()
                            errJob.join()
                        }
                        if (drained == null) drainIncomplete = true
                    }
                } finally {
                    // Runs on every path: normal exit, timeout, cap exceeded, caller
                    // cancellation. destroy() kills the remote process; closing both read
                    // pipes unblocks the drains so coroutineScope can return.
                    tearingDown.set(true)
                    runCatching { process.destroy() }
                    runCatching { stdout.close() }
                    runCatching { stderr.close() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ShellException) {
            throw e
        } catch (e: InterruptedException) {
            throw ShellException.SpawnFailed("interrupted while waiting for the process")
        } catch (e: RuntimeException) {
            throw binderFailureOr(e) { ShellException.SpawnFailed(e.message ?: e::class.java.simpleName) }
        }

        if (timedOut) {
            throw ShellException.SpawnFailed("Command timed out after ${execTimeoutMs}ms without exiting")
        }
        drainError.get()?.let {
            throw ShellException.SpawnFailed("I/O error reading process output: ${it.message ?: it::class.java.simpleName}")
        }
        val truncated = limitExceeded.get() || drainIncomplete
        ShellResult(
            exitCode = if (limitExceeded.get()) -1 else exitCode,
            stdoutBytes = stdoutBuf.toByteArray(),
            stderr = stderrBuf.toString("UTF-8"),
            outputTruncated = truncated,
        )
    }

    /**
     * Copy [input] into [out] up to [cap] bytes. On overflow, keeps the first [cap]
     * bytes, flags [limitExceeded] (the wait loop then kills the process) and stops.
     * Read errors are recorded in [error] unless they were caused by our own teardown.
     */
    private fun drain(
        input: InputStream,
        out: ByteArrayOutputStream,
        cap: Int,
        limitExceeded: AtomicBoolean,
        tearingDown: AtomicBoolean,
        error: AtomicReference<IOException?>,
    ) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) return
                val room = cap - out.size()
                if (n > room) {
                    if (room > 0) out.write(buf, 0, room)
                    limitExceeded.set(true)
                    return
                }
                out.write(buf, 0, n)
            }
        } catch (e: IOException) {
            if (!tearingDown.get()) error.compareAndSet(null, e)
        }
    }

    private fun spawnViaShizuku(argv: Array<String>): ShizukuRemoteProcess {
        val method = newProcessMethod
            ?: throw ShellException.SpawnFailed("Shizuku.newProcess not available on the linked Shizuku-API version")
        return try {
            method.invoke(null, argv, null, null) as ShizukuRemoteProcess
        } catch (e: InvocationTargetException) {
            val cause = e.targetException ?: e
            throw binderFailureOr(cause) {
                ShellException.SpawnFailed("Shizuku.newProcess threw ${cause::class.java.simpleName}: ${cause.message}")
            }
        } catch (e: ReflectiveOperationException) {
            throw ShellException.SpawnFailed("Shizuku.newProcess reflection failed: ${e.message}")
        } catch (e: ClassCastException) {
            throw ShellException.SpawnFailed("Shizuku.newProcess returned an unexpected type: ${e.message}")
        }
    }

    /**
     * Shizuku wraps binder failures (`RemoteException`, incl. `DeadObjectException`)
     * in `RuntimeException`. If [t]'s cause chain contains one, the backend is gone —
     * report [ShellException.NotAvailable]; otherwise use [fallback].
     */
    private inline fun binderFailureOr(t: Throwable, fallback: () -> ShellException): ShellException {
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur is RemoteException) {
                return ShellException.NotAvailable("Shizuku binder call failed (${cur::class.java.simpleName}: ${cur.message}); is the Shizuku service still running?")
            }
            cur = cur.cause
            depth++
        }
        return fallback()
    }

    companion object {
        /**
         * Default upper bound on how long [exec] waits for the spawned process to exit. Without
         * this, a command that never terminates would block the call until the core registry's
         * much longer per-tool timeout.
         */
        const val DEFAULT_EXEC_TIMEOUT_MS = 30_000L

        /** Default per-stream capture cap for text [exec] output (4 MiB). */
        const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024 * 1024

        /** Default stdout capture cap for [execBinary] (64 MiB — fits any screencap PNG). */
        const val DEFAULT_MAX_BINARY_OUTPUT_BYTES = 64 * 1024 * 1024

        /** Server-side wait slice between cancellation / deadline checks. */
        private const val POLL_INTERVAL_MS = 200L

        /** After the process exits, how long to wait for the pipes to reach EOF. */
        private const val DRAIN_GRACE_MS = 2_000L

        /**
         * Cached reflective handle to `Shizuku.newProcess(String[], String[], String)`.
         * Lazily resolved on first use; `null` if the method isn't present on
         * the Shizuku-API version the host linked against, in which case the
         * backend reports a `shell_spawn_failed` error.
         */
        @Volatile
        private var cached: Method? = null

        private val newProcessMethod: Method?
            get() = cached ?: runCatching {
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                ).apply { isAccessible = true }
            }.getOrNull().also { cached = it }
    }
}
