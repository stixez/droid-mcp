package io.droidmcp.flashlight

import android.content.Context
import android.Manifest
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for flashlight/torch tools: [ToggleFlashlightTool] and [SetFlashlightBrightnessTool].
 */
object FlashlightTools {
    /** All flashlight tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ToggleFlashlightTool(context),
        SetFlashlightBrightnessTool(context)
    )

    /** Permissions required by these tools: `CAMERA`. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA
    )

    /** True if the host app holds [requiredPermissions]. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
