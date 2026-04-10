package io.droidmcp.settings

import android.content.Context
import android.media.AudioManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class SetVolumeTool(private val context: Context) : McpTool {

    override val name = "set_volume"
    override val description = "Set volume for a specific audio stream"
    override val parameters = listOf(
        ToolParameter("stream", "Audio stream to adjust: media, ring, alarm, notification (default: media)", ParameterType.STRING),
        ToolParameter("level", "Volume level (0 to max for the stream)", ParameterType.INTEGER, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val streamName = params["stream"]?.toString() ?: "media"
        val level = (params["level"] as? Number)?.toInt()
            ?: return ToolResult.error("level is required")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val streamType = when (streamName.lowercase()) {
            "media" -> AudioManager.STREAM_MUSIC
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> return ToolResult.error("Unknown stream: $streamName. Use: media, ring, alarm, notification")
        }

        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val clamped = level.coerceIn(0, maxVolume)

        return try {
            audioManager.setStreamVolume(streamType, clamped, 0)
            ToolResult.success(mapOf(
                "success" to true,
                "stream" to streamName,
                "level" to clamped,
                "max_level" to maxVolume,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to set volume: ${e.message}")
        }
    }
}
