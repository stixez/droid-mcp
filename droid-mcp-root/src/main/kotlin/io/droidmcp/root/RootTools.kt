package io.droidmcp.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus
import io.droidmcp.shell.ShellTools

/**
 * Provider for the Root-backed shell tools. Wires the shared
 * `:droid-mcp-shell-core` tool set against [RootShellBackend].
 *
 * Tool NAMES are identical to those in
 * `io.droidmcp.shizuku.ShizukuTools` — register one or the other, not both
 * (the second `addTool` call would overwrite the first in the registry).
 * Hosts that want "root if available, else Shizuku" should pick the backend
 * at startup and register tools wired to that one backend.
 *
 * Root activation is a special-access flow: the user installs a superuser
 * manager (Magisk / KernelSU / SuperSU) and grants this app root access on
 * first request. See `docs/ROOT.md`.
 */
object RootTools {

    private val backend = RootShellBackend()

    fun all(context: Context): List<McpTool> = ShellTools.all(context, backend)

    /**
     * No `Manifest.permission.*` runtime grants — root access is checked at
     * `su` time via the superuser manager, gated by [permissionStatus].
     */
    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true

    /**
     * True when libsu reports the host app has been granted root. Non-blocking:
     * returns false if the prompt has never been triggered (call
     * [requestAccess] to trigger it).
     */
    fun isRootAvailable(): Boolean = backend.isAvailable()

    /**
     * Trigger the su prompt (if it hasn't fired yet) on a background thread.
     * Optional [onResult] callback is invoked once libsu has the shell ready,
     * with `true` when the resulting shell is a root shell, `false` otherwise
     * (user denied, non-root device, or a non-root default builder was
     * configured). **The callback runs on libsu's worker thread** — dispatch
     * to the UI thread yourself if you need to mutate UI state.
     *
     * Safe to call multiple times; libsu deduplicates and reuses the cached
     * shell once it's been instantiated.
     */
    fun requestAccess(onResult: ((Boolean) -> Unit)? = null) {
        Shell.getShell { shell ->
            onResult?.invoke(shell.isRoot)
        }
    }

    @Suppress("UNUSED_PARAMETER") // Context kept for API consistency with other modules' permissionStatus
    fun permissionStatus(context: Context): PermissionStatus {
        return when (Shell.isAppGrantedRoot()) {
            null -> PermissionStatus.NotGranted(
                message = "Root access has not been checked yet. Tap Grant Access (or call RootTools.requestAccess()) to trigger the superuser-manager prompt.",
                intent = null,
            )
            false -> PermissionStatus.NotGranted(
                message = "Root access denied by the superuser manager (Magisk / KernelSU / SuperSU). Re-grant in the manager app or reinstall to re-trigger.",
                intent = null,
            )
            true -> PermissionStatus.Granted("Root access granted via libsu")
        }
    }

    /**
     * Returns the same name-set as [ShellTools.allNames] — root mirrors the
     * Shizuku surface exactly.
     */
    @Suppress("UNUSED_PARAMETER") // Context kept for API consistency with other modules
    fun supportedTools(context: Context): Set<String> = ShellTools.allNames()
}
