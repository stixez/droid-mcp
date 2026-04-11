package io.droidmcp.settings

import android.content.Context
import io.droidmcp.core.McpTool

object SettingsTools {

    fun all(context: Context): List<McpTool> = buildList {
        // Read-only tool always available
        add(GetSettingsTool(context))
        // Write tools only if system write permission is granted
        if (android.provider.Settings.System.canWrite(context)) {
            add(SetBrightnessTool(context))
            add(SetVolumeTool(context))
        }
        // WiFi toggle needs its own permission
        if (io.droidmcp.core.PermissionHelper.hasPermissions(
                context, listOf(android.Manifest.permission.CHANGE_WIFI_STATE)
            )
        ) {
            add(ToggleWifiTool(context))
        }
    }

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
