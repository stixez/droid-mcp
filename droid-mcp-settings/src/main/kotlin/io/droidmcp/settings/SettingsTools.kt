package io.droidmcp.settings

import android.Manifest
import android.content.Context
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object SettingsTools {

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

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
