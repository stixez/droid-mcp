package io.droidmcp.calllog

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the call-log tool module. Wires up [ReadCallLogTool] and [SearchCallLogTool],
 * which query `CallLog.Calls` read-only. Requires `READ_CALL_LOG`.
 */
object CallLogTools {

    /** Returns all call-log [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ReadCallLogTool(context),
        SearchCallLogTool(context),
    )

    /** Permissions this module needs: `READ_CALL_LOG`. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CALL_LOG,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
