package io.droidmcp.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class WriteClipboardTool(private val context: Context) : McpTool {

    override val name = "write_clipboard"
    override val description = "Write text to the clipboard"
    override val parameters = listOf(
        ToolParameter("text", "Text content to write to clipboard", ParameterType.STRING, required = true),
        ToolParameter("label", "Label for the clip (default: droid-mcp)", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")
        val label = params["label"]?.toString() ?: "droid-mcp"

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)

        return ToolResult.success(mapOf(
            "success" to true,
            "label" to label,
            "length" to text.length,
        ))
    }
}
