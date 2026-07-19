package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports whether the droid-mcp IME is currently bound with an editor focused, via
 * [InputMethodServiceHolder.isActive]. Always succeeds — no special-access gating, since this
 * tool exists precisely to let the LLM check before calling the gated IME tools. Output:
 * `active` (bound + has an input connection) and `bound` (service instance exists, regardless
 * of connection state).
 */
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
