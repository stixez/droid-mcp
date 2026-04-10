package io.droidmcp.tts

import android.content.Context
import io.droidmcp.core.McpTool

object TtsTools {

    fun all(context: Context): List<McpTool> = listOf(
        SpeakTextTool(context),
        GetTtsInfoTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
