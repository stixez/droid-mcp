package io.droidmcp.audio

import android.content.Context
import io.droidmcp.core.McpTool

object AudioTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetAudioDevicesTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
