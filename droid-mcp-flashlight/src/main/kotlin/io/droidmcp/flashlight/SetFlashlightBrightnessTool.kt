package io.droidmcp.flashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.ParameterType

/**
 * Sets the torch brightness via [CameraManager.turnOnTorchWithStrengthLevel] (level 0 turns it off).
 *
 * Requires Android 13+ (API 33+, `TIRAMISU`) and a camera with `FLASH_INFO_AVAILABLE`; no
 * runtime permission needed (torch control is permission-free, same as [ToggleFlashlightTool]).
 * Returns an error on older API levels.
 *
 * The `level` param is on a 0–255 scale and is mapped linearly onto the camera's native range
 * `1..FLASH_INFO_STRENGTH_MAXIMUM_LEVEL` (any non-zero input maps to at least 1, 255 maps to the
 * maximum). `turnOnTorchWithStrengthLevel` throws for levels above that maximum, so the scaled
 * value is always clamped to it. If the camera reports a maximum of 1 or less (no variable
 * strength support), any non-zero level falls back to plain `setTorchMode(true)`.
 *
 * Output map: `level` (the clamped 0–255 input), `status` ("on"/"off"), `strength_level` (the
 * native level applied; 0 when off), `max_strength_level`, `variable_brightness_supported`.
 */
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
    override val annotations = ToolAnnotations(idempotentHint = true)

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

            val maxStrength = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            val variable = maxStrength > 1

            val strength = when {
                brightnessLevel == 0 -> {
                    cameraManager.setTorchMode(cameraId, false)
                    0
                }
                !variable -> {
                    cameraManager.setTorchMode(cameraId, true)
                    1
                }
                else -> {
                    val scaled = ((brightnessLevel.toLong() * maxStrength + 254) / 255).toInt()
                        .coerceIn(1, maxStrength)
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, scaled)
                    scaled
                }
            }

            return ToolResult.success(mapOf(
                "level" to brightnessLevel,
                "status" to if (brightnessLevel == 0) "off" else "on",
                "strength_level" to strength,
                "max_strength_level" to maxStrength,
                "variable_brightness_supported" to variable,
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
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        }
    }
}
