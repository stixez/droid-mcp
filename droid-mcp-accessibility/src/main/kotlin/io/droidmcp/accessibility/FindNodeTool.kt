package io.droidmcp.accessibility

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class FindNodeTool(private val context: Context) : McpTool {

    override val name = "find_node"
    override val description = "Search the active window's UI tree for nodes matching any combination of text, view-id, class name, or package. Returns up to `limit` matches with the same shape as `query_screen` entries."
    override val parameters = listOf(
        ToolParameter("text", "Substring match against the node's text or content description (case-insensitive).", ParameterType.STRING, required = false),
        ToolParameter("view_id", "Exact match against the node's view-id resource name (e.g. com.app:id/button).", ParameterType.STRING, required = false),
        ToolParameter("class_name", "Exact match against the node's class (e.g. android.widget.Button).", ParameterType.STRING, required = false),
        ToolParameter("package_name", "Exact match against the node's package name. Use to scope a query to a single foreground app (e.g. 'com.whatsapp').", ParameterType.STRING, required = false),
        ToolParameter("limit", "Max matches to return (1-200, default 20).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = (params["text"] as? String)?.takeIf { it.isNotBlank() }
        val viewId = (params["view_id"] as? String)?.takeIf { it.isNotBlank() }
        val className = (params["class_name"] as? String)?.takeIf { it.isNotBlank() }
        val pkg = (params["package_name"] as? String)?.takeIf { it.isNotBlank() }
        if (text == null && viewId == null && className == null && pkg == null) {
            return ToolResult.error("At least one of text, view_id, class_name, or package_name is required.")
        }
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 20

        val payload = NodeQuery.withRoot { root ->
            val matches = mutableListOf<Map<String, Any?>>()
            NodeQuery.walk(root) { node, depth ->
                if (NodeQuery.matches(node, text, viewId, className, pkg)) {
                    matches += NodeQuery.toMap(node, depth)
                }
                matches.size < limit
            }
            mapOf(
                "count" to matches.size,
                "nodes" to matches,
            )
        } ?: return ToolResult.error(notConnectedError())

        return ToolResult.success(payload)
    }
}
