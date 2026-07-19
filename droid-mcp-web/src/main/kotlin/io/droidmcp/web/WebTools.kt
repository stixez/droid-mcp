package io.droidmcp.web

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for network-reaching web tools: [WebSearchTool] (DuckDuckGo scrape) and
 * [FetchWebpageTool] (URL fetch + text extraction).
 */
object WebTools {

    /** All web tools (these take no [context], but the param matches the provider convention). */
    fun all(context: Context): List<McpTool> = listOf(
        WebSearchTool(),
        FetchWebpageTool(),
    )

    /** Permissions required: `INTERNET` (an install-time permission, always granted). */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.INTERNET,
    )

    /** Always true — `INTERNET` is an install-time permission. */
    fun hasPermissions(context: Context): Boolean = true // INTERNET is always granted
}
