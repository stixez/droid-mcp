package io.droidmcp.playback

import android.content.Context
import io.droidmcp.core.McpTool

object PlaybackTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetNowPlayingTool(context),
        MediaControlTool(context),
    )

    // Notification listener is a special permission granted via Settings, not runtime
    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
