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
 * `find_and_tap` — find the first node matching `match` (per `match_kind`) and
 * perform `ACTION_CLICK` on it in one call: a composition of `find_node` +
 * `click_node`.
 *
 * `match_kind` selects the matcher: `text` (default — substring against text +
 * content-description), `desc` (content-description substring), `id` (exact
 * view-id resource name), `class` (exact node class). `case_insensitive`
 * (default true) applies to the substring kinds.
 *
 * Params: required `match`; optional `match_kind`, `case_insensitive`.
 *
 * On success returns `success = true`, `view_id`, `class`, and the echoed
 * `match_kind`. Error codes: `accessibility_not_enabled` (service not bound),
 * `invalid_selector` (missing `match` or unknown `match_kind`), `node_not_found`
 * (no match), `gesture_failed` (ACTION_CLICK returned false on the matched
 * node).
 */
class FindAndTapTool(private val context: Context) : McpTool {

    override val name = "find_and_tap"
    override val description = "Find a node by text / contentDescription / view-id / class and ACTION_CLICK it in one call. Composition of find_node + click_node. Errors with `node_not_found` when the match doesn't appear."
    override val parameters = listOf(
        ToolParameter("match", "The value to match against (matched per `match_kind`).", ParameterType.STRING, required = true),
        ToolParameter("match_kind", "What to match against: 'text' (text + contentDescription substring, default), 'desc' (contentDescription substring), 'id' (exact view-id resource name), 'class' (exact node class name like android.widget.Button).", ParameterType.STRING, required = false),
        ToolParameter("case_insensitive", "Case-insensitive matching for substring kinds. Default true.", ParameterType.BOOLEAN, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (!AccessibilityServiceHolder.isConnected()) {
            return ToolResult.error("accessibility_not_enabled", null)
        }
        val match = (params["match"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_selector", "match is required")
        val kind = (params["match_kind"] as? String)?.lowercase() ?: "text"
        if (kind !in setOf("text", "desc", "id", "class")) {
            return ToolResult.error("invalid_selector", "match_kind must be text|desc|id|class")
        }
        val caseInsensitive = (params["case_insensitive"] as? Boolean) ?: true

        val predicate = predicateFor(kind, match, caseInsensitive)

        val result = NodeQuery.withRoot { root ->
            val node = NodeQuery.findOne(root, predicate) ?: return@withRoot ToolResult.error("node_not_found", null)
            try {
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) {
                    ToolResult.success(mapOf(
                        "success" to true,
                        "view_id" to node.viewIdResourceName,
                        "class" to node.className?.toString(),
                        "match_kind" to kind,
                    ))
                } else {
                    ToolResult.error("gesture_failed", "ACTION_CLICK returned false for matched node")
                }
            } finally {
                node.recycle()
            }
        }
        return result ?: ToolResult.error("accessibility_not_enabled", null)
    }

    private fun predicateFor(
        kind: String,
        match: String,
        caseInsensitive: Boolean,
    ): (AccessibilityNodeInfo) -> Boolean = when (kind) {
        "text" -> { node ->
            val haystack = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            haystack.contains(match, ignoreCase = caseInsensitive)
        }
        "desc" -> { node ->
            node.contentDescription?.toString()?.contains(match, ignoreCase = caseInsensitive) == true
        }
        "id" -> { node -> node.viewIdResourceName == match }
        "class" -> { node -> node.className?.toString() == match }
        else -> { _ -> false }
    }
}
