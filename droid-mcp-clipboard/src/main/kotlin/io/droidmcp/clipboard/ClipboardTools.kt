package io.droidmcp.clipboard

import android.content.Context
import io.droidmcp.core.McpTool

object ClipboardTools {

    fun all(context: Context): List<McpTool> = listOf(
        ReadClipboardTool(context),
        WriteClipboardTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
