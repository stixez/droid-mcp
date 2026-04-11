package io.droidmcp.flashlight

import android.content.Context
import android.Manifest
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object FlashlightTools {
    fun all(context: Context): List<McpTool> = listOf(
        ToggleFlashlightTool(context),
        SetFlashlightBrightnessTool(context)
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
