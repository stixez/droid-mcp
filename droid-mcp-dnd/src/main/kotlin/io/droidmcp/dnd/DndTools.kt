package io.droidmcp.dnd

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object DndTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetDndStatusTool(context),
        SetDndModeTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_NOTIFICATION_POLICY,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
