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

/**
 * `gesture` — dispatch a single-stroke touch gesture via
 * `AccessibilityService.dispatchGesture` and await the system callback so the
 * call returns only when the gesture completes (or is cancelled).
 *
 * The stroke is built from an ordered `points` array (each entry a 2-element
 * `[x, y]` array of screen pixels; minimum 2 points) traced over `duration_ms`.
 * Use it for swipes, drags, or tap-by-coordinate when no node matches. Note:
 * this is a single continuous stroke — it cannot express multi-finger gestures
 * such as pinch.
 *
 * Params: `points` (required), `duration_ms` (optional, clamped 10–3000ms,
 * default 300).
 *
 * On success returns `success = true`, `points` (Int count traced), and
 * `duration_ms`. Errors are long-form messages: malformed/insufficient
 * `points`, the [notConnectedError] message when the service is not bound, and
 * a "gesture canceled / could not be dispatched" message on dispatch failure.
 */
class GestureTool(private val context: Context) : McpTool {

    override val name = "gesture"
    override val description = "Dispatch a touch gesture on the screen via AccessibilityService.dispatchGesture. The gesture is described as a single ordered sequence of points and an optional duration; use this for swipes, drags, or tap-by-coordinate when no node matches. Single-stroke only — cannot express multi-finger gestures like pinch."
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
