package io.droidmcp.web

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool

object WebTools {

    fun all(context: Context): List<McpTool> = listOf(
        WebSearchTool(),
        FetchWebpageTool(),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.INTERNET,
    )

    fun hasPermissions(context: Context): Boolean = true // INTERNET is always granted
}
