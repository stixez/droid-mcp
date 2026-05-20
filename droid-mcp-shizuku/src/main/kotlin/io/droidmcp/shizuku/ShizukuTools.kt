package io.droidmcp.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus
import io.droidmcp.shell.ShellTools
import rikka.shizuku.Shizuku

/**
 * Provider object for the Shizuku-backed shell tools. Wires the shared
 * `:droid-mcp-shell-core` tool set against [ShizukuShellBackend].
 *
 * Shizuku activation is a special-access flow (the user installs the Shizuku
 * app, activates it via wireless debugging on Android 11+ or ADB, then grants
 * the runtime permission to this host app). See `docs/SHIZUKU.md`.
 */
object ShizukuTools {

    private val backend = ShizukuShellBackend()

    fun all(context: Context): List<McpTool> = ShellTools.all(context, backend)

    /**
     * No `Manifest.permission.*` runtime grants — Shizuku's permission is its
     * own runtime check via `Shizuku.checkSelfPermission()`, gated by
     * [permissionStatus].
     */
    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true

    /**
     * True when both the Shizuku binder is reachable AND the host has been
     * granted the permission. False if Shizuku isn't installed/activated or
     * if the user hasn't granted access.
     */
    fun isShizukuReady(): Boolean = backend.isAvailable()

    /**
     * Open the Shizuku app's permission flow. If Shizuku is installed and
     * pingable, calls `Shizuku.requestPermission(requestCode)` which surfaces
     * a system dialog. If not installed, returns a launch intent for the
     * Play Store / install screen.
     */
    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    /**
     * Settings-style intent for the host to launch when Shizuku isn't ready —
     * either opens Shizuku itself (so the user can activate it) or the Play
     * Store install page if Shizuku isn't installed yet. The host activity
     * should add `Intent.FLAG_ACTIVITY_NEW_TASK` if launching from a
     * non-activity context.
     */
    fun installOrOpenIntent(context: Context): Intent {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) return launchIntent
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"),
        )
    }

    fun permissionStatus(context: Context): PermissionStatus {
        // 1. Shizuku binder unreachable → either not installed or not activated.
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            val isInstalled = runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
            val message = if (isInstalled) {
                "Shizuku is installed but not activated. Open Shizuku and start the service via wireless debugging (Android 11+) or ADB."
            } else {
                "Shizuku is not installed. Install it from the Play Store and activate via wireless debugging or ADB."
            }
            return PermissionStatus.NotGranted(message, installOrOpenIntent(context))
        }
        // 2. Binder reachable but permission not granted → user must allow this host.
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return PermissionStatus.NotGranted(
                message = "Shizuku is running but the host app hasn't been granted permission. Call ShizukuTools.requestPermission(requestCode) from an Activity.",
                intent = null,
            )
        }
        return PermissionStatus.Granted("Shizuku is ready (binder reachable, permission granted)")
    }

    fun supportedTools(context: Context): Set<String> = ShellTools.allNames()

    internal const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}
