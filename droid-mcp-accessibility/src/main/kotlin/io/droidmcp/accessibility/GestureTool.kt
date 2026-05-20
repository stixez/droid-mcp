package io.droidmcp.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GestureTool(private val context: Context) : McpTool {

    override val name = "gesture"
    override val description = "Dispatch a touch gesture on the screen via AccessibilityService.dispatchGesture. The gesture is described as a sequence of points and an optional duration; use this for swipes, drags, pinches (multi-stroke), or tap-by-coordinate when no node matches."
    override val parameters = listOf(
        ToolParameter("points", "Array of [x, y] integer screen coordinates the stroke walks through, in order. Minimum 2 points (start, end). Each entry must be a 2-element array of numbers; malformed entries reject the entire call.", ParameterType.ARRAY, required = true),
        ToolParameter("duration_ms", "Duration of the stroke in milliseconds (10-3000, default 300).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val svc = AccessibilityServiceHolder.service ?: return ToolResult.error(notConnectedError())

        val raw = params["points"] as? List<*>
            ?: return ToolResult.error("points must be an array")
        val coords = ArrayList<Pair<Float, Float>>(raw.size)
        for ((i, entry) in raw.withIndex()) {
            val pair = entry as? List<*>
                ?: return ToolResult.error("points[$i] is not a 2-element [x, y] array")
            if (pair.size != 2) {
                return ToolResult.error("points[$i] must have exactly 2 elements (got ${pair.size})")
            }
            val x = (pair[0] as? Number)?.toFloat()
                ?: return ToolResult.error("points[$i][0] is not a number")
            val y = (pair[1] as? Number)?.toFloat()
                ?: return ToolResult.error("points[$i][1] is not a number")
            coords += x to y
        }
        if (coords.size < 2) return ToolResult.error("Need at least 2 [x, y] points (start + end).")
        val duration = (params["duration_ms"] as? Number)?.toLong()?.coerceIn(10L, 3_000L) ?: 300L

        val path = Path().apply {
            moveTo(coords.first().first, coords.first().second)
            coords.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val completed = suspendCancellableCoroutine<Boolean> { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(g: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val dispatched = svc.dispatchGesture(gesture, callback, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }

        return if (completed) {
            ToolResult.success(mapOf("success" to true, "points" to coords.size, "duration_ms" to duration))
        } else {
            ToolResult.error("Gesture was canceled or could not be dispatched.")
        }
    }
}
