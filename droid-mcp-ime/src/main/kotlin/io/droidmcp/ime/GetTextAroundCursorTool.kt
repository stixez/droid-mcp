package io.droidmcp.ime

import android.content.Context
import android.text.InputType
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reads the text surrounding the cursor via `InputConnection.getTextBeforeCursor` /
 * `getTextAfterCursor`. Requires the droid-mcp IME to be active with an editor focused, else
 * [imeNotActiveError]. Android does **not** mask password fields for the active IME — the
 * keyboard can read whatever is in the field — so this tool enforces the guard itself: when
 * the bound editor's `EditorInfo.inputType` is a password variation (text `PASSWORD`,
 * `VISIBLE_PASSWORD`, `WEB_PASSWORD`, or number `PASSWORD`; see [isPasswordInputType]) it
 * refuses with the short-form error `password_field`.
 * Output: `before` and `after` strings (empty if the editor has no more text in that
 * direction).
 */
class GetTextAroundCursorTool(private val context: Context) : McpTool {

    override val name = "get_text_around_cursor"
    override val description = "Read text before and after the cursor in the focused editor via InputConnection.getTextBeforeCursor / getTextAfterCursor. Privacy: refuses with error `password_field` when the focused editor is a password input (text/visible/web password or numeric PIN variations)."
    override val parameters = listOf(
        ToolParameter("before", "Max characters before the cursor (1-2000, default 200).", ParameterType.INTEGER, required = false),
        ToolParameter("after", "Max characters after the cursor (1-2000, default 200).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ic = InputMethodServiceHolder.service?.connection()
            ?: return ToolResult.error(imeNotActiveError())
        val inputType = InputMethodServiceHolder.currentEditorInfo()?.inputType ?: 0
        if (isPasswordInputType(inputType)) {
            return ToolResult.error("password_field", "refusing to read text from a password input")
        }
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

/**
 * True when [inputType] (an `EditorInfo.inputType` bitmask) describes a password input:
 * `TYPE_CLASS_TEXT` with the `PASSWORD`, `VISIBLE_PASSWORD`, or `WEB_PASSWORD` variation, or
 * `TYPE_CLASS_NUMBER` with `TYPE_NUMBER_VARIATION_PASSWORD` (PIN fields).
 */
internal fun isPasswordInputType(inputType: Int): Boolean {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}
