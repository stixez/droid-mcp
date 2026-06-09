package io.droidmcp.screenshot

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the screenshot tool ([CaptureScreenTool]).
 *
 * Screen capture relies on MediaProjection consent obtained by the host
 * Activity and registered via [MediaProjectionHolder], not on runtime
 * permissions.
 */
object ScreenshotTools {

    /** Instantiates the screenshot tool bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        CaptureScreenTool(context),
    )

    /**
     * No runtime permissions: MediaProjection consent is handled by the host
     * Activity (see [MediaProjectionHolder]).
     */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true`; capture readiness depends on [MediaProjectionHolder], not permissions. */
    fun hasPermissions(context: Context): Boolean = true
}
