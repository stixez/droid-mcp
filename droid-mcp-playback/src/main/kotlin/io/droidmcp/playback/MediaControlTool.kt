package io.droidmcp.playback

import android.content.Context
import android.media.session.MediaSessionManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class MediaControlTool(private val context: Context) : McpTool {

    override val name = "media_control"
    override val description = "Send playback control commands to the active media session. Requires notification listener access."
    override val parameters = listOf(
        ToolParameter("command", "Command: 'play', 'pause', 'stop', 'next', 'previous'", ParameterType.STRING, required = true),
        ToolParameter("package_name", "Target a specific app's media session by package name", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val command = params["command"]?.toString()
            ?: return ToolResult.error("command is required")
        val targetPackage = params["package_name"]?.toString()

        if (command !in listOf("play", "pause", "stop", "next", "previous")) {
            return ToolResult.error("command must be one of: play, pause, stop, next, previous")
        }

        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return ToolResult.error("MediaSessionManager not available")

        val listenerComponent = NotificationListenerHolder.componentName
            ?: return ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")

        val controllers = try {
            sessionManager.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            return ToolResult.error("Notification listener permission required. Enable it in Settings > Apps > Special access > Notification access.")
        }

        val controller = if (targetPackage != null) {
            controllers.find { it.packageName == targetPackage }
                ?: return ToolResult.error("No active media session for package: $targetPackage")
        } else {
            controllers.firstOrNull()
                ?: return ToolResult.error("No active media sessions")
        }

        val controls = controller.transportControls

        when (command) {
            "play" -> controls.play()
            "pause" -> controls.pause()
            "stop" -> controls.stop()
            "next" -> controls.skipToNext()
            "previous" -> controls.skipToPrevious()
        }

        return ToolResult.success(mapOf(
            "success" to true,
            "command" to command,
            "target_package" to controller.packageName,
        ))
    }
}
