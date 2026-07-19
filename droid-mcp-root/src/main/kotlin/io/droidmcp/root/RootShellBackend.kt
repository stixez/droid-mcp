package io.droidmcp.root

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import io.droidmcp.shell.ShellBackend
import io.droidmcp.shell.ShellException
import io.droidmcp.shell.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * [ShellBackend] backed by libsu — runs commands as the `root` UID. Strictly
 * more powerful than Shizuku's `ShizukuShellBackend`: writes to
 * `/system`, freezes apps via `pm hide`, reads `/data/data/<pkg>`, etc.
 *
 * **Output model.** libsu's [Shell.Result.out] is `List<String>` (UTF-8
 * line-decoded). [exec] joins those lines back into a byte stream via UTF-8
 * re-encoding which is **lossy for binary stdout** (the PNG signature
 * `89 50 4E 47 ...` is invalid UTF-8, so the round-trip mangles it). Tools
 * whose stdout is raw bytes — `screencap -p`, anything streaming
 * non-UTF-8 — must call [execBinary], which uses a temp-file roundtrip
 * via [SuFileInputStream] to read raw bytes.
 *
 * **Host responsibility — do not override libsu's default builder to a
 * non-root config.** [requestAccess][io.droidmcp.root.RootTools.requestAccess]
 * calls [Shell.getShell] which uses whatever default builder is configured.
 * If your host has called `Shell.setDefaultBuilder(...)` with non-root flags
 * before any droid-mcp code runs, the prompt will never fire and root tools
 * will silently fail with `shell_unavailable`. libsu's untouched default
 * prefers root, which is the configuration droid-mcp expects.
 *
 * **Command parsing.** libsu writes each `Shell.cmd(String)` invocation to
 * the stdin of a long-running interactive shell process — it is NOT a
 * `sh -c <string>` wrap. The shell still parses each line per `sh` syntax,
 * so argv quoting via [ShellUtils.escapedString] is required for any value
 * that could contain shell metacharacters.
 */
class RootShellBackend : ShellBackend {

    override val name: String = "Root (libsu)"

    override fun isAvailable(): Boolean = Shell.isAppGrantedRoot() == true

    override suspend fun exec(command: String, args: List<String>): ShellResult = withContext(Dispatchers.IO) {
        ensureRootGranted()

        val commandLine = buildCommandLine(command, args)
        val result = try {
            Shell.cmd(commandLine).exec()
        } catch (t: Throwable) {
            throw ShellException.SpawnFailed(t.message ?: t::class.java.simpleName)
        }

        ShellResult(
            exitCode = result.code,
            stdoutBytes = result.out.joinToString("\n").toByteArray(Charsets.UTF_8),
            stderr = result.err.joinToString("\n"),
        )
    }

    /**
     * Binary-safe path: writes stdout to a tempfile under `/data/local/tmp`
     * (root-only), then reads it raw via [SuFileInputStream]. Used by
     * `capture_screen_quiet` for PNG bytes.
     */
    override suspend fun execBinary(command: String, args: List<String>): ShellResult = withContext(Dispatchers.IO) {
        ensureRootGranted()

        val tempPath = "/data/local/tmp/droidmcp-${UUID.randomUUID()}"
        val commandLine = buildCommandLine(command, args) + " > " + ShellUtils.escapedString(tempPath)

        val result = try {
            Shell.cmd(commandLine).exec()
        } catch (t: Throwable) {
            // The shell-side redirect (`> tempPath`) may have already created the file even
            // though exec() itself threw — clean it up so it doesn't orphan in /data/local/tmp.
            runCatching { SuFile(tempPath).delete() }
            throw ShellException.SpawnFailed(t.message ?: t::class.java.simpleName)
        }

        val stdoutBytes: ByteArray = if (result.code == 0) {
            try {
                SuFileInputStream.open(tempPath).use { input ->
                    ByteArrayOutputStream().use { output ->
                        input.copyTo(output)
                        output.toByteArray()
                    }
                }
            } finally {
                runCatching { SuFile(tempPath).delete() }
            }
        } else {
            runCatching { SuFile(tempPath).delete() }
            ByteArray(0)
        }

        ShellResult(
            exitCode = result.code,
            stdoutBytes = stdoutBytes,
            stderr = result.err.joinToString("\n"),
        )
    }

    private fun ensureRootGranted() {
        when (Shell.isAppGrantedRoot()) {
            null -> throw ShellException.NotAvailable(
                "Root access not yet checked. Call RootTools.requestAccess() from an Activity to trigger the su prompt."
            )
            false -> throw ShellException.PermissionDenied(
                "Root access denied by the superuser manager (Magisk / KernelSU / SuperSU)."
            )
            true -> Unit
        }
    }

    private fun buildCommandLine(command: String, args: List<String>): String = buildString {
        append(ShellUtils.escapedString(command))
        args.forEach { arg ->
            append(' ')
            append(ShellUtils.escapedString(arg))
        }
    }
}
