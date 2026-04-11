package io.droidmcp.flashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.ParameterType

class SetFlashlightBrightnessTool(private val context: Context) : McpTool {
    override val name = "set_flashlight_brightness"
    override val description = "Set flashlight brightness level (Android 13+ only, API 33+)"
    override val parameters = listOf(
        ToolParameter(
            "level",
            "Brightness level from 0-255 (0 = off, 1-255 = on at specified brightness)",
            ParameterType.INTEGER,
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return ToolResult.error("Flashlight brightness control requires Android 13+ (API 33+)")
        }

        val level = params["level"] as? Number
            ?: return ToolResult.error("level parameter is required and must be a number")

        val brightnessLevel = level.toInt().coerceIn(0, 255)

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult.error("CameraManager not available")

        try {
            val cameraId = getFlashCameraId(cameraManager)
                ?: return ToolResult.error("No camera with flash available")

            if (brightnessLevel == 0) {
                cameraManager.setTorchMode(cameraId, false)
            } else {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, brightnessLevel)
            }

            return ToolResult.success(mapOf(
                "level" to brightnessLevel,
                "status" to if (brightnessLevel == 0) "off" else "on"
            ))
        } catch (e: CameraAccessException) {
            return ToolResult.error("Failed to set flashlight brightness: ${e.message}")
        } catch (e: Exception) {
            return ToolResult.error("Error: ${e.message}")
        }
    }

    private fun getFlashCameraId(manager: CameraManager): String? {
        return try {
            manager.cameraIdList.find { id ->
                manager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        }
    }
}
