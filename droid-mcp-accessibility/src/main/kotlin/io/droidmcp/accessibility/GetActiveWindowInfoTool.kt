package io.droidmcp.accessibility

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetActiveWindowInfoTool(private val context: Context) : McpTool {

    override val name = "get_active_window_info"
    override val description = "Return the foreground package name plus the root node's class (typically the foreground activity's view-root class). Cheap; safe to poll."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val payload = NodeQuery.withRoot { root ->
            mapOf(
                "package_name" to root.packageName?.toString(),
                "root_class" to root.className?.toString(),
                "window_id" to root.windowId,
            )
        } ?: return ToolResult.error(notConnectedError())
        return ToolResult.success(payload)
    }
}
