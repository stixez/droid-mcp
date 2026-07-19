package io.droidmcp.clipboard

import android.content.ClipboardManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reads the system primary clip via `ClipboardManager`, coercing the first item to text. No
 * permissions required (though on API 29+ the OS only allows access to the foreground app or
 * default IME). Output always includes `has_content` (whether a primary clip exists), `is_text`
 * (clip MIME type starts with `text/`), and `text` (coerced string, or null when empty/unavailable).
 */
class ReadClipboardTool(private val context: Context) : McpTool {

    override val name = "read_clipboard"
    override val description = "Read the current clipboard content"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (!clipboard.hasPrimaryClip()) {
            return ToolResult.success(mapOf(
                "has_content" to false,
                "is_text" to false,
                "text" to null,
            ))
        }

        val clip = clipboard.primaryClip
            ?: return ToolResult.success(mapOf(
                "has_content" to false,
                "is_text" to false,
                "text" to null,
            ))

        val item = clip.getItemAt(0)
        val text = item?.coerceToText(context)?.toString()
        val isText = clip.description.hasMimeType("text/*")

        return ToolResult.success(mapOf(
            "has_content" to true,
            "is_text" to isText,
            "text" to text,
        ))
    }
}
