package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class SetSelectionTool(private val context: Context) : McpTool {

    override val name = "set_selection"
    override val description = "Move the cursor or select a range via InputConnection.setSelection. Pass equal start/end to position the cursor without selecting."
    override val parameters = listOf(
        ToolParameter("start", "Selection start (absolute character offset, >=0).", ParameterType.INTEGER, required = true),
        ToolParameter("end", "Selection end (>= start).", ParameterType.INTEGER, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ic = InputMethodServiceHolder.service?.connection()
            ?: return ToolResult.error(imeNotActiveError())
        val start = (params["start"] as? Number)?.toInt()
            ?: return ToolResult.error("start is required (integer)")
        val end = (params["end"] as? Number)?.toInt()
            ?: return ToolResult.error("end is required (integer)")
        if (start < 0 || end < start) {
            return ToolResult.error("Require start >= 0 and end >= start.")
        }
        val ok = ic.setSelection(start, end)
        return if (ok) {
            ToolResult.success(mapOf("success" to true, "start" to start, "end" to end))
        } else {
            ToolResult.error("setSelection returned false.")
        }
    }
}
