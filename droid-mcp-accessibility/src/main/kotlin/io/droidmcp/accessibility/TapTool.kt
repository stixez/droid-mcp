package io.droidmcp.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `tap` — dispatch a single-tap gesture (a ~50ms stroke via [tapPath]) at the
 * given screen coordinates and await completion. Use when no node matches the
 * selector or for raw coord-based input.
 *
 * Params: required `x`, `y` (screen pixels; must be non-negative, non-NaN).
 *
 * On success returns `success = true`, `x`, `y`. Error codes:
 * `accessibility_not_enabled` (service not bound), `invalid_coords`
 * (non-numeric / negative / NaN), `gesture_failed` (dispatch cancelled or
 * rejected).
 */
class TapTool(private val context: Context) : McpTool {

    override val name = "tap"
    override val description = "Dispatch a single-tap gesture at the given screen coordinates via the accessibility service. Use when no node matches the selector or for raw coord-based input."
    override val parameters = listOf(
        ToolParameter("x", "Screen X coordinate in pixels.", ParameterType.NUMBER, required = true),
        ToolParameter("y", "Screen Y coordinate in pixels.", ParameterType.NUMBER, required = true),
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

        val stroke = GestureDescription.StrokeDescription(tapPath(x, y), 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val completed = dispatchAwait(svc, gesture)
        return if (completed) {
            ToolResult.success(mapOf("success" to true, "x" to x, "y" to y))
        } else {
            ToolResult.error("gesture_failed", "tap dispatch was cancelled or rejected")
        }
    }
}
