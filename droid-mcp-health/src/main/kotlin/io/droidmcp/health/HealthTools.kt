package io.droidmcp.health

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the health/fitness tools: [GetStepCountTool] and [GetActivityInfoTool].
 * Only step counting is permission-gated; [GetActivityInfoTool] needs no permission.
 */
object HealthTools {

    /** All health [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetStepCountTool(context),
        GetActivityInfoTool(context),
    )

    /**
     * ACTIVITY_RECOGNITION is required on Android 10+ (API 29+) to read step counter sensor data.
     * On older versions no permission is needed.
     */
    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyList()
        }

    /** True when [requiredPermissions] are granted (always true below API 29). */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
