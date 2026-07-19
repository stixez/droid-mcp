package io.droidmcp.vibration

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for vibration tools: [VibrateTool] and [VibratePatternTool]. Requires the `VIBRATE`
 * permission (a normal install-time permission).
 */
object VibrationTools {

    /** Both vibration tools. */
    fun all(context: Context): List<McpTool> = listOf(
        VibrateTool(context),
        VibratePatternTool(context),
    )

    /** The `VIBRATE` permission. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.VIBRATE
    )

    /** True when `VIBRATE` is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
