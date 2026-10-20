package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Moves the cursor or selects a range via `InputConnection.setSelection`. Requires the
 * droid-mcp IME to be active with an editor focused, else [imeNotActiveError]. Both offsets
 * are absolute character positions; `start` must be `>= 0` and `end` must be `>= start` (pass
 * equal values to position the cursor without selecting). Output on success: `success` (true)
 * and the echoed `start`/`end`.
 */
class SetSelectionTool(private val context: Context) : McpTool {

    override val name = "set_selection"
    override val description = "Move the cursor (equal start and end) or select a range of text in the focused field, by character offsets."
    override val parameters = listOf(
        ToolParameter("start", "Selection start (absolute character offset, >=0).", ParameterType.INTEGER, required = true, minimum = 0.0),
        ToolParameter("end", "Selection end (>= start).", ParameterType.INTEGER, required = true, minimum = 0.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        boolean("success")
        integer("start")
        integer("end")
    }

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
