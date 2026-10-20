package io.droidmcp.accessibility

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `get_active_window_info` — return the foreground package plus the active
 * window's root-node class (typically the foreground activity's view-root
 * class). Cheap and read-only; safe to poll.
 *
 * No params.
 *
 * On success returns `package_name` (String?), `root_class` (String?), and
 * `window_id` (Int). Returns the long-form [notConnectedError] message when the
 * service is not bound, and `no_active_window` when it is bound but no
 * active-window root is available (see [rootUnavailableError]).
 */
class GetActiveWindowInfoTool(private val context: Context) : McpTool {

    override val name = "get_active_window_info"
    override val description = "Get the foreground app's package and its root view class. Cheap enough to poll."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        string("package_name", nullable = true)
        string("root_class", nullable = true)
        integer("window_id")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val payload = NodeQuery.withRoot { root ->
            mapOf(
                "package_name" to root.packageName?.toString(),
                "root_class" to root.className?.toString(),
                "window_id" to root.windowId,
            )
        } ?: return rootUnavailableError(shortForm = false)
        return ToolResult.success(payload)
    }
}
