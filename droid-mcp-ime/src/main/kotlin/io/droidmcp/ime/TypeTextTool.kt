package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class TypeTextTool(private val context: Context) : McpTool {

    override val name = "type_text"
    override val description = "Commit text into the currently focused editor field via InputConnection.commitText. Requires the droid-mcp IME to be active."
    override val parameters = listOf(
        ToolParameter("text", "Text to insert at the cursor.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ic = InputMethodServiceHolder.service?.connection()
            ?: return ToolResult.error(imeNotActiveError())
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")
        val ok = ic.commitText(text, 1)
        return if (ok) {
            ToolResult.success(mapOf("success" to true, "length" to text.length))
        } else {
            ToolResult.error("commitText returned false; the InputConnection may have been invalidated.")
        }
    }
}
