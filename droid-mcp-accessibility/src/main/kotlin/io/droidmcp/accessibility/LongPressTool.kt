package io.droidmcp.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class LongPressTool(private val context: Context) : McpTool {

    override val name = "long_press"
    override val description = "Dispatch a long-press gesture at the given screen coordinates. Holds for `duration_ms` (default 800ms)."
    override val parameters = listOf(
        ToolParameter("x", "Screen X coordinate in pixels.", ParameterType.NUMBER, required = true),
        ToolParameter("y", "Screen Y coordinate in pixels.", ParameterType.NUMBER, required = true),
        ToolParameter("duration_ms", "Hold duration in milliseconds (100-5000, default 800).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val svc = AccessibilityServiceHolder.service
            ?: return ToolResult.error("accessibility_not_enabled", null)
        val x = (params["x"] as? Number)?.toFloat()
            ?: return ToolResult.error("invalid_coords", "x must be a number")
        val y = (params["y"] as? Number)?.toFloat()
            ?: return ToolResult.error("invalid_coords", "y must be a number")
        if (x < 0 || y < 0 || x.isNaN() || y.isNaN()) {
            return ToolResult.error("invalid_coords", "x and y must be non-negative numbers")
        }
        val duration = (params["duration_ms"] as? Number)?.toLong()?.coerceIn(100L, 5000L) ?: 800L

        val stroke = GestureDescription.StrokeDescription(tapPath(x, y), 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val completed = dispatchAwait(svc, gesture)
        return if (completed) {
            ToolResult.success(mapOf("success" to true, "x" to x, "y" to y, "duration_ms" to duration))
        } else {
            ToolResult.error("gesture_failed", "long_press dispatch was cancelled or rejected")
        }
    }
}
