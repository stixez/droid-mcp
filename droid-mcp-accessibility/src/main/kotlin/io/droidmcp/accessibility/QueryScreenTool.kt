@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `query_screen` — dump the active window's [AccessibilityNodeInfo] tree as a
 * flat, ranked list of node projections.
 *
 * The full tree is walked breadth-first, then **ranked by usefulness** before
 * truncation: clickable (+4) > has-text/content-description (+2) > scrollable
 * (+1) > rest, with BFS order preserved as the tie-break within each rank.
 * This ordering is a documented contract — when the result is truncated to
 * `max_nodes`, the highest-ranked nodes are the ones kept, so token-bound
 * callers can trust the prefix. Password-flagged nodes are emitted with
 * `is_password = true` and their `text` / `content_description` masked to null
 * (see [NodeQuery.toMap]).
 *
 * Params: `max_nodes` (optional, clamped 1–2000, default 500).
 *
 * On success returns `count` (Int, nodes returned after truncation),
 * `truncated` (Boolean, true when the full tree exceeded `max_nodes`),
 * `package_name` (String?, foreground package), and `nodes` (List of node
 * projection maps, each shaped like [NodeQuery.toMap]).
 *
 * Returns the short-form error `accessibility_not_enabled` when the host's
 * [DroidMcpAccessibilityService] is not bound.
 */
class QueryScreenTool(private val context: Context) : McpTool {

    override val name = "query_screen"
    override val description = "Dump the active window's AccessibilityNodeInfo tree as a flat list of nodes with text / bounds / class / view-id / action capabilities. **Returns nodes ranked by usefulness:** clickable > has-text > scrollable > rest. When truncated to `max_nodes`, the highest-ranked nodes are kept — token-bound callers can trust the order. Password-flagged nodes report `is_password = true` with text / content_description masked."
    override val parameters = listOf(
        ToolParameter("max_nodes", "Cap on the number of nodes to return (1-2000, default 500).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val maxNodes = (params["max_nodes"] as? Number)?.toInt()?.coerceIn(1, 2000) ?: 500

        val payload = NodeQuery.withRoot { root ->
            // Walk the full tree before ranking so a deep clickable can't be
            // dropped by a premature BFS cap. Accessibility trees are bounded
            // by on-screen UI complexity (~hundreds of nodes on heavy screens,
            // sub-ms to walk), so a buffer cap isn't worth the correctness
            // gap it creates.
            val candidates = mutableListOf<RankedNode>()
            NodeQuery.walk(root) { node, depth ->
                candidates += RankedNode(rank = rankOf(node), projection = NodeQuery.toMap(node, depth))
                true
            }
            val sorted = candidates.sortedByDescending { it.rank }.take(maxNodes)
            val truncated = candidates.size > maxNodes
            mapOf(
                "count" to sorted.size,
                "truncated" to truncated,
                "package_name" to root.packageName?.toString(),
                "nodes" to sorted.map { it.projection },
            )
        } ?: return ToolResult.error("accessibility_not_enabled", null)

        return ToolResult.success(payload)
    }

    private data class RankedNode(val rank: Int, val projection: Map<String, Any?>)

    private fun rankOf(node: AccessibilityNodeInfo): Int {
        val clickable = if (node.isClickable) 4 else 0
        val hasText = if (!node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) 2 else 0
        val scrollable = if (node.isScrollable) 1 else 0
        return clickable + hasText + scrollable
    }
}

/**
 * Long-form "service not bound" message used by the pre-0.7.0 tools
 * ([FindNodeTool], the [NodeActionTools] family, [GestureTool],
 * [GlobalActionTool], [GetActiveWindowInfoTool], [TakeScreenshotViaA11yTool]).
 *
 * Note the deliberate split in the module's wire contract: these tools surface
 * this human-readable sentence, whereas the 0.7.0 coord/find tools ([TapTool],
 * [LongPressTool], [FindAndTapTool], [ScrollToFindTool]) and the polling tools
 * ([QueryScreenTool], [WaitForTextTool]) emit the short-form code
 * `accessibility_not_enabled` instead. Both indicate the same condition.
 */
internal fun notConnectedError(): String =
    "Accessibility service not bound. Enable the host app's accessibility service in Settings > Accessibility > Installed apps."
