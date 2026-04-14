package io.droidmcp.intent

import android.content.Context
import android.content.Intent
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ShareContentTool(private val context: Context) : McpTool {

    override val name = "share_content"
    override val description = "Share text content via the Android share sheet (ACTION_SEND)"
    override val parameters = listOf(
        ToolParameter("text", "Text content to share", ParameterType.STRING, required = true),
        ToolParameter("subject", "Optional subject line (used by email apps)", ParameterType.STRING),
        ToolParameter("type", "MIME type (default: 'text/plain')", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")
        val subject = params["subject"]?.toString()
        val type = params["type"]?.toString() ?: "text/plain"

        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(chooser)
            ToolResult.success(mapOf(
                "success" to true,
                "text_length" to text.length,
                "type" to type,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to share content: ${e.message}")
        }
    }
}
