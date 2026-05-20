package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class IsImeActiveTool(private val context: Context) : McpTool {

    override val name = "is_ime_active"
    override val description = "Check whether the droid-mcp IME is currently the active keyboard and has an editor field focused. Use this to gate other IME tools."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val active = InputMethodServiceHolder.isActive()
        return ToolResult.success(mapOf(
            "active" to active,
            "bound" to (InputMethodServiceHolder.service != null),
        ))
    }
}
