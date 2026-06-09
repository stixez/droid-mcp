package io.droidmcp.tts

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for text-to-speech tools: [SpeakTextTool] and [GetTtsInfoTool].
 */
object TtsTools {

    /** All TTS tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        SpeakTextTool(context),
        GetTtsInfoTool(context),
    )

    /** No permissions required. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true — these tools need no permissions. */
    fun hasPermissions(context: Context): Boolean = true
}
