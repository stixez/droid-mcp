package io.droidmcp.print

import android.content.Context
import io.droidmcp.core.McpTool

object PrintTools {

    fun all(context: Context): List<McpTool> = listOf(
        ListPrintersTool(context),
        PrintContentTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
