package io.droidmcp.settings

import android.content.Context
import io.droidmcp.core.McpTool

object SettingsTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetSettingsTool(context),
        SetBrightnessTool(context),
        SetVolumeTool(context),
        ToggleWifiTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        android.Manifest.permission.WRITE_SETTINGS,
        android.Manifest.permission.CHANGE_WIFI_STATE,
    )

    fun hasPermissions(context: Context): Boolean =
        android.provider.Settings.System.canWrite(context) &&
        io.droidmcp.core.PermissionHelper.hasPermissions(context, listOf(android.Manifest.permission.CHANGE_WIFI_STATE))
}
