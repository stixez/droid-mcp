package io.droidmcp.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Sets screen brightness (0-255, clamped). Requires `WRITE_SETTINGS`; when not granted it launches
 * the system grant screen and returns an error instead of writing.
 *
 * Output keys on success: `success` (true), `brightness` (the clamped value applied).
 */
class SetBrightnessTool(private val context: Context) : McpTool {

    override val name = "set_brightness"
    override val description = "Set screen brightness level (0-255). Requires WRITE_SETTINGS permission."
    override val parameters = listOf(
        ToolParameter("level", "Brightness level (0-255)", ParameterType.INTEGER, required = true),
    )
    override val annotations = ToolAnnotations(idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return ToolResult.error("WRITE_SETTINGS permission not granted. Opening settings to grant it.")
        }

        val level = (params["level"] as? Number)?.toInt()
            ?: return ToolResult.error("level is required")
        val clamped = level.coerceIn(0, 255)

        return try {
            // Adaptive brightness (SCREEN_BRIGHTNESS_MODE_AUTOMATIC) continuously overrides
            // SCREEN_BRIGHTNESS from the ambient light sensor, silently discarding this write
            // unless manual mode is forced first.
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
            ToolResult.success(mapOf(
                "success" to true,
                "brightness" to clamped,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to set brightness: ${e.message}")
        }
    }
}
