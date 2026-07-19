package io.droidmcp.shell

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Factory for the shell-based tool set. Backend-modules (`:droid-mcp-shizuku`,
 * eventually `:droid-mcp-root`) call [all] with their concrete [ShellBackend]
 * and expose the resulting list under a module-specific provider
 * (`ShizukuTools`, `RootTools`).
 *
 * The LLM-facing tool names are identical regardless of backend — the host
 * app picks which backend module to depend on at startup.
 */
object ShellTools {

    /**
     * The full 0.8.0 shell-tool set, wired against [shell].
     */
    fun all(context: Context, shell: ShellBackend): List<McpTool> = listOf(
        // PM
        InstallApkTool(shell),
        UninstallAppTool(shell),
        ClearAppDataTool(shell),
        ForceStopAppTool(shell),
        DisableAppTool(shell),
        EnableAppTool(shell),
        // Permissions
        GrantPermissionTool(shell),
        RevokePermissionTool(shell),
        ListAppPermissionsTool(shell),
        // Settings
        PutSecureSettingTool(shell),
        PutGlobalSettingTool(shell),
        PutSystemSettingTool(shell),
        // Dumpsys (just the cheap ones in 0.8.0; batterystats / procstats / notifications dumpsys deferred)
        GetTopWindowTool(shell),
        // Standby
        SetAppStandbyBucketTool(shell),
        MakeAppInactiveTool(shell),
        // Screencap
        CaptureScreenQuietTool(shell),
        // Escape hatch — host-allowlist-gated
        RunShellTool(shell),
    )

    /**
     * Tool names — useful for `supportedTools(context)` projections in
     * downstream providers that need the canonical name set.
     */
    fun allNames(): Set<String> = setOf(
        "install_apk", "uninstall_app", "clear_app_data",
        "force_stop_app", "disable_app", "enable_app",
        "grant_permission", "revoke_permission", "list_app_permissions",
        "put_secure_setting", "put_global_setting", "put_system_setting",
        "get_top_window",
        "set_app_standby_bucket", "make_app_inactive",
        "capture_screen_quiet",
        "run_shell",
    )
}
