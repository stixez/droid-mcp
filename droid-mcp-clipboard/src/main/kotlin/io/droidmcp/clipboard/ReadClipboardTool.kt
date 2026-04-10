package io.droidmcp.clipboard

import android.content.ClipboardManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ReadClipboardTool(private val context: Context) : McpTool {

    override val name = "read_clipboard"
    override val description = "Read the current clipboard content"
    override val parameters = emptyList<ToolParameter>()

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
            || clip.description.hasMimeType("text/plain")

        return ToolResult.success(mapOf(
            "has_content" to true,
            "is_text" to isText,
            "text" to text,
        ))
    }
}
