package io.droidmcp.calllog

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object CallLogTools {

    fun all(context: Context): List<McpTool> = listOf(
        ReadCallLogTool(context),
        SearchCallLogTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CALL_LOG,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
