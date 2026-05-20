package io.droidmcp.shizuku

import io.droidmcp.shell.ShellBackend
import io.droidmcp.shell.ShellException
import io.droidmcp.shell.ShellResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/**
 * [ShellBackend] backed by Shizuku's `newProcess` binder. Runs commands as the
 * `shell` UID — broader than the host app but narrower than root.
 *
 * Activation is the user's responsibility: install the Shizuku app, activate
 * it via wireless debugging (Android 11+) or ADB, grant the permission. The
 * backend reports `isAvailable() == false` until both the binder is reachable
 * and the host has permission. See `docs/SHIZUKU.md`.
 *
 * **Implementation note (Shizuku v13):** `Shizuku.newProcess(...)` is package-
 * private in v13. We reflectively access it because the alternative (a full
 * AIDL UserService pattern) is substantially more code for the same effective
 * behaviour. Migrating to a proper UserService is tracked for a follow-up
 * release; reflection is the documented pragmatic path used by other Shizuku
 * consumers in the v13 era.
 */
class ShizukuShellBackend : ShellBackend {

    override val name: String = "Shizuku"

    override fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    override suspend fun exec(command: String, args: List<String>): ShellResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) {
            throw ShellException.NotAvailable("Shizuku binder not reachable; install/activate Shizuku and try again")
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw ShellException.PermissionDenied("Shizuku permission not granted to this app; call Shizuku.requestPermission() from an Activity")
        }

        val argv = (listOf(command) + args).toTypedArray()
        val process = try {
            spawnViaShizuku(argv)
        } catch (e: ShellException) {
            throw e
        } catch (e: Throwable) {
            throw ShellException.SpawnFailed(e.message ?: e::class.java.simpleName)
        }

        val stdoutBuf = ByteArrayOutputStream()
        val stderrBuf = ByteArrayOutputStream()
        // Drain stdout + stderr concurrently to avoid deadlock when the
        // process fills one pipe buffer while we're reading the other.
        // Structured concurrency: coroutineScope joins all children before
        // returning, and on cancellation propagates to the drain jobs.
        // process.destroy() lives in the INNER try/finally so it runs BEFORE
        // coroutineScope's children-join phase — closing the pipes promptly
        // unblocks the drain children's blocking copyTo calls so they can
        // exit and coroutineScope can return.
        coroutineScope {
            val outJob = launch(Dispatchers.IO) {
                runCatching { process.inputStream.use { it.copyTo(stdoutBuf) } }
            }
            val errJob = launch(Dispatchers.IO) {
                runCatching { process.errorStream.use { it.copyTo(stderrBuf) } }
            }
            try {
                // runInterruptible converts InterruptedException to
                // CancellationException so the coroutine cancellation reaches
                // the blocked waitFor() call. Without it, coroutine cancel
                // would have to wait for the process to exit naturally.
                val exit = runInterruptible(Dispatchers.IO) { process.waitFor() }
                outJob.join()
                errJob.join()
                ShellResult(
                    exitCode = exit,
                    stdoutBytes = stdoutBuf.toByteArray(),
                    stderr = stderrBuf.toString("UTF-8"),
                )
            } finally {
                runCatching { process.destroy() }
            }
        }
    }

    private fun spawnViaShizuku(argv: Array<String>): Process {
        val method = newProcessMethod
            ?: throw ShellException.SpawnFailed("Shizuku.newProcess not available on the linked Shizuku-API version")
        return try {
            method.invoke(null, argv, null, null) as Process
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException ?: e
            throw ShellException.SpawnFailed("Shizuku.newProcess threw ${cause::class.java.simpleName}: ${cause.message}")
        } catch (e: ReflectiveOperationException) {
            throw ShellException.SpawnFailed("Shizuku.newProcess reflection failed: ${e.message}")
        }
    }

    companion object {
        /**
         * Cached reflective handle to `Shizuku.newProcess(String[], String[], String)`.
         * Lazily resolved on first use; `null` if the method isn't present on
         * the Shizuku-API version the host linked against, in which case the
         * backend reports a `shell_spawn_failed` error.
         */
        @Volatile
        private var cached: java.lang.reflect.Method? = null

        private val newProcessMethod: java.lang.reflect.Method?
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
