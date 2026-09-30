@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.delay

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
 * (clamped 1–20, default 5). Each step scrolls the container that can move in
 * `direction` (semantic scroll action, or a swipe inside it as a fallback), then
 * re-checks the tree every 150 ms for up to 1 s while it catches up — including
 * after the final scroll.
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
        ToolParameter("direction", "'down' (default) / 'up' / 'left' / 'right'. Uses reading semantics: 'down' reveals content below.", ParameterType.STRING, required = false, enumValues = listOf("down", "up", "left", "right")),
        ToolParameter("max_scrolls", "Max scroll iterations (1-20, default 5).", ParameterType.INTEGER, required = false, minimum = 1.0, maximum = 20.0),
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

        // Swipe inside the largest scrollable container on screen: a list that doesn't cover the
        // screen center (e.g. below a header) never sees a full-screen center swipe. Falls back
        // to the whole screen when nothing reports itself scrollable.
        val area = scrollTargetBounds(direction) ?: run {
            val metrics = context.resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }

        // Reading-direction → finger-physics swipe path.
        val (startX, startY, endX, endY) = swipeCoordsFor(
            direction, area.width().toFloat(), area.height().toFloat(), area.left.toFloat(), area.top.toFloat(),
        )

        // maxScrolls swipes means maxScrolls + 1 tree checks: one before any
        // swipe, one after each swipe (including the last).
        for (iteration in 0..maxScrolls) {
            // The accessibility tree trails the scroll (Compose/semantics updates plus event
            // delivery), so after a scroll keep re-checking for a while instead of once.
            val foundNode = if (iteration == 0) findMatch(match) else awaitMatch(match)
            if (foundNode != null) {
                return ToolResult.success(mapOf(
                    "found" to true,
                    "scrolls" to iteration,
                    "node" to foundNode,
                ))
            }
            if (iteration == maxScrolls) break

            // Not found; scroll once. Prefer asking the right container to scroll itself (what
            // screen readers do — page-sized, independent of layout and of where the list sits
            // on screen); fall back to a swipe inside it.
            if (semanticScroll(direction)) continue
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

    /**
     * The first node matching [match] in the active window, as a result map; null if none.
     *
     * With [refreshScrollables], scrollable containers are re-fetched (`refresh()`) before their
     * children are read. The service's node cache otherwise keeps serving a container's
     * pre-scroll child list for the rest of the call, so newly scrolled-in items stay invisible
     * until the next tool call. `walk` visits a node before reading its children, so the refresh
     * lands exactly where the stale data is. One IPC per scrollable; works on every API level.
     */
    private fun findMatch(match: String, refreshScrollables: Boolean = false): Map<String, Any?>? = NodeQuery.withRoot { root ->
        var hit: Map<String, Any?>? = null
        NodeQuery.walk(root) { node, depth ->
            if (refreshScrollables && node.isScrollable) node.refresh()
            if (NodeQuery.matches(node, match, null, null, null)) {
                hit = NodeQuery.toMap(node, depth)
                false // stop walking — we have our match
            } else {
                true
            }
        }
        hit
    }

    /** Re-checks for [match] every [POLL_MS] until [SETTLE_TIMEOUT_MS] passes (a null root counts as not found). */
    private suspend fun awaitMatch(match: String): Map<String, Any?>? {
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        while (true) {
            delay(POLL_MS)
            findMatch(match, refreshScrollables = true)?.let { return it }
            if (System.currentTimeMillis() >= deadline) return null
        }
    }

    /**
     * Scrolls the best container for [direction] with the matching directional action
     * (`ACTION_SCROLL_DOWN` / `UP` / `LEFT` / `RIGHT`). False when nothing on screen can scroll
     * that way (the caller then swipes).
     */
    private fun semanticScroll(direction: String): Boolean {
        val action = directionalAction(direction)
        val target = scrollTargetBounds(direction) ?: return false
        return NodeQuery.withRoot { root ->
            val node = NodeQuery.findOne(root, { supportsAction(it, action) && boundsOf(it) == target })
                ?: return@withRoot false
            try {
                node.performAction(action.id)
            } finally {
                node.recycle()
            }
        } ?: false
    }

    /**
     * Bounds of the largest node that can scroll in [direction]; on a tie the innermost wins.
     * A tab pager and the vertical list inside it often share bounds — only the list supports
     * scrolling down, so it's chosen for down/up and the pager for left/right.
     */
    private fun scrollTargetBounds(direction: String): Rect? = NodeQuery.withRoot { root ->
        val action = directionalAction(direction)
        var best: Rect? = null
        NodeQuery.walk(root) { node, _ ->
            if (supportsAction(node, action)) {
                val r = boundsOf(node)
                val area = r.width().toLong() * r.height()
                val bestArea = best?.let { it.width().toLong() * it.height() } ?: -1L
                if (!r.isEmpty && area >= bestArea) best = r // >= : later (deeper) nodes win ties
            }
            true
        }
        best
    }

    private fun directionalAction(direction: String): AccessibilityNodeInfo.AccessibilityAction = when (direction) {
        "down" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
        "up" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
        "right" -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT
        else -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT
    }

    private fun supportsAction(node: AccessibilityNodeInfo, action: AccessibilityNodeInfo.AccessibilityAction): Boolean =
        node.actionList.any { it.id == action.id }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    private companion object {
        /** How long to keep re-checking after a scroll before scrolling again. */
        const val SETTLE_TIMEOUT_MS = 1_000L

        /** Interval between re-checks while the tree catches up with a scroll. */
        const val POLL_MS = 150L
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
 * Uses 35% offsets around the area's center for a substantial scroll distance.
 *
 * @param direction One of `down` / `up` / `left` / `right` (already validated
 *   by the caller; any other value throws).
 * @param width Width of the area to swipe in (the screen, or a scrollable container), in pixels.
 * @param height Height of that area.
 * @param left Screen x of the area's left edge (0 for the whole screen).
 * @param top Screen y of the area's top edge.
 * @return The [SwipeCoords] for the stroke that reveals content in [direction].
 */
internal fun swipeCoordsFor(
    direction: String,
    width: Float,
    height: Float,
    left: Float = 0f,
    top: Float = 0f,
): SwipeCoords {
    val cx = left + width / 2f
    val cy = top + height / 2f
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
