package io.droidmcp.flashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.ParameterType

class ToggleFlashlightTool(private val context: Context) : McpTool {
    override val name = "toggle_flashlight"
    override val description = "Toggle the flashlight on or off"
    override val parameters = listOf(
        ToolParameter(
            "enabled",
            "Whether to enable (true) or disable (false) the flashlight",
            ParameterType.BOOLEAN,
            required = true
        )
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val enabled = params["enabled"] as? Boolean
            ?: return ToolResult.error("enabled parameter is required and must be a boolean")

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolResult.error("CameraManager not available")

        try {
            val cameraId = getFlashCameraId(cameraManager)
                ?: return ToolResult.error("No camera with flash available")

            cameraManager.setTorchMode(cameraId, enabled)

            return ToolResult.success(mapOf(
                "status" to if (enabled) "on" else "off",
                "enabled" to enabled
            ))
        } catch (e: CameraAccessException) {
            return ToolResult.error("Failed to toggle flashlight: ${e.message}")
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
