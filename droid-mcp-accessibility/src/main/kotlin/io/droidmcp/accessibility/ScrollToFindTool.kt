@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `scroll_to_find` — repeatedly swipe in `direction` until `match` appears in
 * the active window's UI tree or `max_scrolls` is exhausted. The current screen
 * is checked first, so a match already visible on iteration 0 returns without
 * any swipe.
 *
 * `direction` uses **reading semantics** — it names where the content you want
 * lives, not where the finger moves (the inversion is handled by
 * [swipeCoordsFor]):
 * - `down` → reveal content below the current viewport (finger swipes up)
 * - `up` → reveal content above (finger swipes down)
 * - `right` → reveal content to the right (finger swipes left)
 * - `left` → reveal content to the left (finger swipes right)
 *
 * `match` is a case-insensitive substring against text + content-description.
 *
 * Params: required `match`; optional `direction` (default `down`), `max_scrolls`
 * (clamped 1–20, default 5).
 *
 * On success returns `found = true`, `scrolls` (Int iterations performed before
 * the hit), and `node` (the matched node projection, shaped like
 * [NodeQuery.toMap]). Error codes: `accessibility_not_enabled` (service not
 * bound), `invalid_selector` (missing `match` or bad `direction`),
 * `gesture_failed` (a swipe was cancelled), `scroll_exhausted` (no match after
 * `max_scrolls`).
 */
class ScrollToFindTool(private val context: Context) : McpTool {

    override val name = "scroll_to_find"
    override val description = "Repeatedly swipe in `direction` (reading semantics: 'down' reveals content below, 'up' reveals content above, 'left'/'right' likewise) until `match` appears in the active window's UI tree or `max_scrolls` exhausts. Returns the matched node's selector fields on hit."
    override val parameters = listOf(
        ToolParameter("match", "Substring to find in the UI tree (matched against text + contentDescription, case-insensitive).", ParameterType.STRING, required = true),
        ToolParameter("direction", "'down' (default) / 'up' / 'left' / 'right'. Uses reading semantics: 'down' reveals content below.", ParameterType.STRING, required = false),
        ToolParameter("max_scrolls", "Max scroll iterations (1-20, default 5).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val svc = AccessibilityServiceHolder.service
            ?: return ToolResult.error("accessibility_not_enabled", null)
        val match = (params["match"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_selector", "match is required")
        val direction = (params["direction"] as? String)?.lowercase() ?: "down"
        if (direction !in setOf("down", "up", "left", "right")) {
            return ToolResult.error("invalid_selector", "direction must be down|up|left|right")
        }
        val maxScrolls = (params["max_scrolls"] as? Number)?.toInt()?.coerceIn(1, 20) ?: 5

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        // Reading-direction → finger-physics swipe path.
        val (startX, startY, endX, endY) = swipeCoordsFor(direction, width, height)

        repeat(maxScrolls) { iteration ->
            // Check current screen first — match may already be visible on iteration 0.
            val foundNode = NodeQuery.withRoot { root ->
                var hit: Map<String, Any?>? = null
                NodeQuery.walk(root) { node, depth ->
                    if (NodeQuery.matches(node, match, null, null, null)) {
                        hit = NodeQuery.toMap(node, depth)
                        false // stop walking — we have our match
                    } else {
                        true
                    }
                }
                hit
            }
            if (foundNode != null) {
                return ToolResult.success(mapOf(
                    "found" to true,
                    "scrolls" to iteration,
                    "node" to foundNode,
                ))
            }

            // Not found; dispatch one swipe and try again.
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 300L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val swiped = dispatchAwait(svc, gesture)
            if (!swiped) {
                return ToolResult.error("gesture_failed", "scroll swipe ${iteration + 1} was cancelled")
            }
        }

        return ToolResult.error("scroll_exhausted", "no match after $maxScrolls scrolls")
    }

}

/**
 * Start/end coordinates of a single swipe stroke (screen pixels), as returned
 * by [swipeCoordsFor].
 *
 * @property startX Stroke start X.
 * @property startY Stroke start Y.
 * @property endX Stroke end X.
 * @property endY Stroke end Y.
 */
internal data class SwipeCoords(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

/**
 * Translate a reading-direction into finger-physics swipe coordinates. Lifted
 * to internal so the direction-inversion math is testable without spinning up
 * an AccessibilityService. The mapping is the locked contract:
 *
 *   - `down`  → finger sweeps UP   → content moves up   → reveals what was below
 *   - `up`    → finger sweeps DOWN → content moves down → reveals what was above
 *   - `right` → finger sweeps LEFT → content shifts left → reveals what was to the right
 *   - `left`  → finger sweeps RIGHT → content shifts right → reveals what was to the left
 *
 * Uses 35% offsets around the screen center for a substantial scroll distance.
 *
 * @param direction One of `down` / `up` / `left` / `right` (already validated
 *   by the caller; any other value throws).
 * @param width Screen width in pixels.
 * @param height Screen height in pixels.
 * @return The [SwipeCoords] for the stroke that reveals content in [direction].
 */
internal fun swipeCoordsFor(
    direction: String,
    width: Float,
    height: Float,
): SwipeCoords {
    val cx = width / 2f
    val cy = height / 2f
    val dy = height * 0.35f
    val dx = width * 0.35f
    return when (direction) {
        // reveal content below → finger moves up
        "down" -> SwipeCoords(cx, cy + dy, cx, cy - dy)
        // reveal content above → finger moves down
        "up" -> SwipeCoords(cx, cy - dy, cx, cy + dy)
        // reveal content to the right → finger moves left
        "right" -> SwipeCoords(cx + dx, cy, cx - dx, cy)
        // reveal content to the left → finger moves right
        "left" -> SwipeCoords(cx - dx, cy, cx + dx, cy)
        else -> error("unreachable direction $direction")
    }
}
