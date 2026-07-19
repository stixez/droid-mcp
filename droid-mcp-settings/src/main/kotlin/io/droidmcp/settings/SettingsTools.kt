package io.droidmcp.settings

import android.Manifest
import android.content.Context
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for device settings tools.
 *
 * Exposes a read tool ([GetSettingsTool]) that is always available, plus write tools that are
 * conditionally registered: [SetBrightnessTool] and [SetVolumeTool] require `WRITE_SETTINGS`
 * (gated via [Settings.System.canWrite]), and [ToggleWifiTool] requires `CHANGE_WIFI_STATE`.
 * The module itself reports no required permissions because the read surface needs none.
 */
object SettingsTools {

    /** All settings tools available for the current grant state: read tool always, write tools when permitted. */
    fun all(context: Context): List<McpTool> = buildList {
        // Read-only tool always available
        add(GetSettingsTool(context))
        // Write tools only if system write permission is granted
        if (Settings.System.canWrite(context)) {
            add(SetBrightnessTool(context))
            add(SetVolumeTool(context))
        }
        // WiFi toggle needs its own permission
        if (PermissionHelper.hasPermissions(
                context, listOf(Manifest.permission.CHANGE_WIFI_STATE)
            )
        ) {
            add(ToggleWifiTool(context))
        }
    }

    /** No permissions required for the read surface; write tools self-gate in [all]. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true` — the read tool needs no permission. */
    fun hasPermissions(context: Context): Boolean = true
}
