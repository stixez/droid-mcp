package io.droidmcp.ime

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reads the text surrounding the cursor via `InputConnection.getTextBeforeCursor` /
 * `getTextAfterCursor`. Requires the droid-mcp IME to be active with an editor focused, else
 * [imeNotActiveError]. Password fields (`TYPE_TEXT_VARIATION_PASSWORD`) and `FLAG_SECURE`
 * windows are not delivered plaintext by the platform, so those return empty/masked content.
 * Output: `before` and `after` strings (empty if the editor has no more text in that
 * direction).
 */
class GetTextAroundCursorTool(private val context: Context) : McpTool {

    override val name = "get_text_around_cursor"
    override val description = "Read text before and after the cursor in the focused editor via InputConnection.getTextBeforeCursor / getTextAfterCursor. Privacy note: Android does not deliver plaintext to IMEs for fields with TYPE_TEXT_VARIATION_PASSWORD set (or windows with FLAG_SECURE), so password inputs return empty or masked content."
    override val parameters = listOf(
        ToolParameter("before", "Max characters before the cursor (1-2000, default 200).", ParameterType.INTEGER, required = false),
        ToolParameter("after", "Max characters after the cursor (1-2000, default 200).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ic = InputMethodServiceHolder.service?.connection()
            ?: return ToolResult.error(imeNotActiveError())
        val before = (params["before"] as? Number)?.toInt()?.coerceIn(1, 2000) ?: 200
        val after = (params["after"] as? Number)?.toInt()?.coerceIn(1, 2000) ?: 200
        val textBefore = ic.getTextBeforeCursor(before, 0)?.toString() ?: ""
        val textAfter = ic.getTextAfterCursor(after, 0)?.toString() ?: ""
        return ToolResult.success(mapOf(
            "before" to textBefore,
            "after" to textAfter,
        ))
    }
}
