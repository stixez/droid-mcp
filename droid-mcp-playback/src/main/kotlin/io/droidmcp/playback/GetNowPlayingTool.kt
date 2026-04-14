package io.droidmcp.playback

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetNowPlayingTool(private val context: Context) : McpTool {

    override val name = "get_now_playing"
    override val description = "Get information about the currently playing media from active media sessions. Requires notification listener access."
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return ToolResult.error("MediaSessionManager not available")

        val listenerComponent = NotificationListenerHolder.componentName
            ?: return ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")

        val controllers = try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            return ToolResult.error("Notification listener permission required. Enable it in Settings > Apps > Special access > Notification access.")
        }

        if (controllers.isEmpty()) {
            return ToolResult.success(mapOf(
                "playing" to false,
                "message" to "No active media sessions",
            ))
        }

        val sessions = controllers.map { controller ->
            val metadata = controller.metadata
            val state = controller.playbackState

            buildMap<String, Any?> {
                put("package", controller.packageName)
                put("state", state?.let { playbackStateToString(it.state) } ?: "unknown")
                put("position_ms", state?.position)
                put("title", metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
                put("artist", metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))
                put("album", metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM))
                put("duration_ms", metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 })
            }
        }

        return ToolResult.success(mapOf(
            "playing" to sessions.any { it["state"] == "playing" },
            "sessions" to sessions,
        ))
    }

    private fun playbackStateToString(state: Int): String = when (state) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_CONNECTING -> "connecting"
        PlaybackState.STATE_ERROR -> "error"
        PlaybackState.STATE_FAST_FORWARDING -> "fast_forwarding"
        PlaybackState.STATE_REWINDING -> "rewinding"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "skipping_next"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "skipping_previous"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "skipping_to_item"
        else -> "none"
    }
}
