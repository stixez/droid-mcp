package io.droidmcp.shell

import io.droidmcp.core.ToolResult

/**
 * Optional host-side denylist for the dedicated shell tools. Passed to
 * [ShellTools.all] (and the `ShizukuTools` / `RootTools` providers); the LLM
 * cannot change it.
 *
 * The dedicated tools are powerful even without `run_shell`: `put_secure_setting`
 * can enable an accessibility service or notification listener, switch the
 * default IME or turn on ADB; `grant_permission` can grant development
 * permissions such as `WRITE_SECURE_SETTINGS`. So the default everywhere is
 * [RECOMMENDED], which denies the keys in [RECOMMENDED_DENIED_SETTING_KEYS] and
 * the permissions in [RECOMMENDED_DENIED_PERMISSIONS]. A host that really needs
 * those writes opts out explicitly:
 *
 * ```kotlin
 * ShizukuTools.all(context, ShellPolicy.PERMISSIVE)
 * ```
 *
 * Denied calls return a `denied_by_policy` error without spawning anything.
 *
 * @property deniedSettingKeys settings keys `put_secure_setting` / `put_global_setting` /
 *   `put_system_setting` refuse to write, in any namespace. Matched case-insensitively.
 * @property deniedPermissions permissions `grant_permission` refuses to grant. Matched
 *   exactly (permission names are case-sensitive). `revoke_permission` is not affected.
 */
data class ShellPolicy(
    val deniedSettingKeys: Set<String> = emptySet(),
    val deniedPermissions: Set<String> = emptySet(),
) {
    private val deniedKeysLower: Set<String> = deniedSettingKeys.map { it.lowercase() }.toSet()

    /** True if writing settings key [key] is denied. */
    fun isSettingKeyDenied(key: String): Boolean = key.lowercase() in deniedKeysLower

    /** True if granting [permission] is denied. */
    fun isPermissionDenied(permission: String): Boolean = permission in deniedPermissions

    companion object {
        /** Denies nothing. Opt-in only; the default is [RECOMMENDED]. */
        val PERMISSIVE = ShellPolicy()

        /**
         * Settings keys that hand another component (or a remote host) broad control
         * of the device: accessibility / notification-listener / IME selection, ADB,
         * developer options, and package-verifier toggles.
         */
        val RECOMMENDED_DENIED_SETTING_KEYS: Set<String> = setOf(
            "enabled_accessibility_services",
            "accessibility_enabled",
            "enabled_notification_listeners",
            "enabled_notification_assistant",
            "default_input_method",
            "enabled_input_methods",
            "adb_enabled",
            "adb_wifi_enabled",
            "development_settings_enabled",
            "install_non_market_apps",
            "package_verifier_enable",
            "verifier_verify_adb_installs",
        )

        /** Development / signature-level permissions that `pm grant` can hand out. */
        val RECOMMENDED_DENIED_PERMISSIONS: Set<String> = setOf(
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.READ_LOGS",
            "android.permission.DUMP",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.INTERACT_ACROSS_USERS",
            "android.permission.SET_PROCESS_LIMIT",
            "android.permission.CHANGE_CONFIGURATION",
        )

        /** [RECOMMENDED_DENIED_SETTING_KEYS] + [RECOMMENDED_DENIED_PERMISSIONS]. */
        val RECOMMENDED = ShellPolicy(
            deniedSettingKeys = RECOMMENDED_DENIED_SETTING_KEYS,
            deniedPermissions = RECOMMENDED_DENIED_PERMISSIONS,
        )
    }
}

internal fun policyDenied(detail: String): ToolResult = ToolResult.error("denied_by_policy", detail)
