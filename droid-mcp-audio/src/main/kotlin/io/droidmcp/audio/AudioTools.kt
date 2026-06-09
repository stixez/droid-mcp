package io.droidmcp.audio

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the audio module. Exposes [GetAudioDevicesTool]; no permissions required.
 */
object AudioTools {

    /** The single audio tool. */
    fun all(context: Context): List<McpTool> = listOf(
        GetAudioDevicesTool(context),
    )

    /** No permissions required. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true`. */
    fun hasPermissions(context: Context): Boolean = true
}
