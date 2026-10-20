package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Deletes text around the cursor via `InputConnection.deleteSurroundingText(before, after)`.
 * Requires the droid-mcp IME to be active with an editor focused, else [imeNotActiveError].
 * At least one of `before`/`after` must be non-zero. Output on success: `success` (true) and
 * the echoed `before`/`after` counts.
 */
class DeleteTextTool(private val context: Context) : McpTool {

    override val name = "delete_text"
    override val description = "Delete a number of characters before and/or after the cursor in the focused text field."
    override val parameters = listOf(
        ToolParameter("before", "Characters to delete before the cursor (0-2000, default 0).", ParameterType.INTEGER, required = false, minimum = 0.0, maximum = 2000.0),
        ToolParameter("after", "Characters to delete after the cursor (0-2000, default 0).", ParameterType.INTEGER, required = false, minimum = 0.0, maximum = 2000.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        boolean("success")
        integer("before")
        integer("after")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ic = InputMethodServiceHolder.service?.connection()
            ?: return ToolResult.error(imeNotActiveError())
        val before = (params["before"] as? Number)?.toInt()?.coerceIn(0, 2000) ?: 0
        val after = (params["after"] as? Number)?.toInt()?.coerceIn(0, 2000) ?: 0
        if (before == 0 && after == 0) {
            return ToolResult.error("At least one of before or after must be > 0.")
        }
        val ok = ic.deleteSurroundingText(before, after)
        return if (ok) {
            ToolResult.success(mapOf("success" to true, "before" to before, "after" to after))
        } else {
            ToolResult.error("deleteSurroundingText returned false.")
        }
    }
}
