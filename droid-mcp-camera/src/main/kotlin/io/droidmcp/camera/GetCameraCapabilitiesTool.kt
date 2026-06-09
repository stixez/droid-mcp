package io.droidmcp.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enumerates the device's cameras via Camera2 [CameraManager], reporting each camera's id, lens
 * facing, flash availability, and maximum JPEG output resolution. Querying characteristics needs no
 * runtime permission; requires camera hardware (the manifest marks the camera feature non-required).
 *
 * Result keys: `cameras` (list of maps with `id`, `facing` (`front`/`back`/`external`/`unknown`),
 * `flash_available`, `max_resolution` like `"4032x3024"`), `count`.
 */
class GetCameraCapabilitiesTool(private val context: Context) : McpTool {

    override val name = "get_camera_capabilities"
    override val description = "Get information about available cameras and their capabilities"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList

            val cameras = cameraIds.map { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)

                val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "front"
                    CameraCharacteristics.LENS_FACING_BACK -> "back"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
                    else -> "unknown"
                }

                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val outputSizes = configMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                val maxResolution = outputSizes?.maxByOrNull { it.width * it.height }?.let { "${it.width}x${it.height}" }
                    ?: "unknown"

                mapOf(
                    "id" to id,
                    "facing" to facing,
                    "flash_available" to flashAvailable,
                    "max_resolution" to maxResolution,
                )
            }

            ToolResult.success(mapOf(
                "cameras" to cameras,
                "count" to cameras.size,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to get camera capabilities: ${e.message}")
        }
    }
}
