package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Switches the active keyboard back to the user's previous IME via
 * `InputMethodService.switchToPreviousInputMethod`. Unlike the other IME tools this only needs
 * the service bound (not necessarily an editor focused), so the LLM can hand control back even
 * without a live input connection. Returns an error if the service isn't bound, or if the
 * system rejects the switch (e.g. no previous IME recorded).
 */
class SwitchToPreviousImeTool(private val context: Context) : McpTool {

    override val name = "switch_to_previous_ime"
    override val description = "Switch back to the user's previous keyboard via InputMethodService.switchToPreviousInputMethod. Call this when the LLM is done driving the editor so normal typing resumes."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val svc = InputMethodServiceHolder.service
            ?: return ToolResult.error("droid-mcp IME service not bound.")
        val ok = runCatching { svc.switchToPreviousInputMethod() }.getOrDefault(false)
        return if (ok) {
            ToolResult.success(mapOf("success" to true))
        } else {
            ToolResult.error("switchToPreviousInputMethod returned false — no previous IME recorded, or the system rejected the switch.")
        }
    }
}
