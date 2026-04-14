package io.droidmcp.screenshot

import android.content.Context
import io.droidmcp.core.McpTool

object ScreenshotTools {

    fun all(context: Context): List<McpTool> = listOf(
        CaptureScreenTool(context),
    )

    // MediaProjection consent is handled by the host Activity, not runtime permissions
    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
