package io.droidmcp.screen

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports current screen state: interactivity, rotation, brightness, and keyguard lock. No
 * permissions required. `brightness` is the raw `Settings.System.SCREEN_BRIGHTNESS` value (0–255),
 * or -1 if it could not be read (e.g. adaptive brightness).
 *
 * Output map: `is_screen_on` (Boolean), `rotation` (Int degrees: 0/90/180/270), `brightness` (Int),
 * `is_locked` (Boolean).
 */
class GetScreenStateTool(private val context: Context) : McpTool {

    override val name = "get_screen_state"
    override val description = "Get current screen state including whether the screen is on, rotation, brightness, and lock state"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = powerManager.isInteractive

        @Suppress("DEPRECATION")
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val rotation = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) { -1 }

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        return ToolResult.success(mapOf(
            "is_screen_on" to isScreenOn,
            "rotation" to rotation,
            "brightness" to brightness,
            "is_locked" to isLocked,
        ))
    }
}
