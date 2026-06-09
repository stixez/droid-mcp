package io.droidmcp.clipboard

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the clipboard tool module. Wires up [ReadClipboardTool] and
 * [WriteClipboardTool], which use the system `ClipboardManager`. No permissions required.
 */
object ClipboardTools {

    /** Returns all clipboard [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ReadClipboardTool(context),
        WriteClipboardTool(context),
    )

    /** No permissions are required for clipboard access. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true — clipboard tools need no runtime permission. */
    fun hasPermissions(context: Context): Boolean = true
}
