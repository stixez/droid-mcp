package io.droidmcp.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class VibrateTool(private val context: Context) : McpTool {

    override val name = "vibrate"
    override val description = "Vibrate the device for a specified duration"
    override val parameters = listOf(
        ToolParameter("duration_ms", "Duration of vibration in milliseconds (1-10000)", ParameterType.INTEGER, required = true),
        ToolParameter("amplitude", "Vibration amplitude (1-255), or null for default", ParameterType.INTEGER, required = false),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val duration = (params["duration_ms"] as? Number)?.toInt()?.coerceIn(1, 10000)
            ?: return ToolResult.error("duration_ms must be between 1 and 10000")

        val amplitude = (params["amplitude"] as? Number)?.toInt()?.coerceIn(1, 255)

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            return ToolResult.error("Device does not have a vibrator")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (amplitude != null) {
                    VibrationEffect.createOneShot(duration.toLong(), amplitude)
                } else {
                    VibrationEffect.createOneShot(duration.toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration.toLong())
            }
            ToolResult.success(mapOf("success" to true))
        } catch (e: Exception) {
            ToolResult.error("Failed to vibrate: ${e.message}")
        }
    }
}
