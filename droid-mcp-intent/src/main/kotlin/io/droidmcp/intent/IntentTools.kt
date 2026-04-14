package io.droidmcp.intent

import android.content.Context
import io.droidmcp.core.McpTool

object IntentTools {

    fun all(context: Context): List<McpTool> = listOf(
        SendIntentTool(context),
        ShareContentTool(context),
        OpenDeepLinkTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
