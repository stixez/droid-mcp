package io.droidmcp.vibration

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object VibrationTools {

    fun all(context: Context): List<McpTool> = listOf(
        VibrateTool(context),
        VibratePatternTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.VIBRATE
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
