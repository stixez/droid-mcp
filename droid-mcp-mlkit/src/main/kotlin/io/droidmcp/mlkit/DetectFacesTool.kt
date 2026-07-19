package io.droidmcp.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Detects faces in a local image with on-device ML Kit face detection (accurate mode, full
 * classification). Returns bounding boxes plus expression/eye-open probabilities and head Euler
 * angles. Does NOT identify or recognize individuals.
 *
 * No permissions required; `image_path` is sandboxed to the external-storage root via
 * [PathValidator] and must point at an existing file. Read-only.
 *
 * Output keys: `face_count`, `faces` (each with `bounding_box` {`left`, `top`, `right`, `bottom`},
 * `smiling_probability`, `left_eye_open_probability`, `right_eye_open_probability`,
 * `head_euler_angle_y`, `head_euler_angle_z`).
 */
class DetectFacesTool(private val context: Context) : McpTool {

    override val name = "detect_faces"
    override val description = "Detect faces in an image using ML Kit. Returns bounding boxes and expression probabilities. Does NOT perform identification."
    override val parameters = listOf(
        ToolParameter("image_path", "Absolute path to an image under external storage", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val path = params["image_path"]?.toString()
            ?: return@withContext ToolResult.error("image_path is required")
        if (!PathValidator.isAllowed(path)) {
            return@withContext ToolResult.error("Access denied: image_path is outside allowed storage directories")
        }
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return@withContext ToolResult.error("Image file not found: $path")
        }

        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            FaceDetection.getClient(options).use { detector ->
                val faces = detector.process(image).awaitResult().map { face ->
                    val box = face.boundingBox
                    mapOf(
                        "bounding_box" to mapOf(
                            "left" to box.left,
                            "top" to box.top,
                            "right" to box.right,
                            "bottom" to box.bottom,
                        ),
                        "smiling_probability" to face.smilingProbability,
                        "left_eye_open_probability" to face.leftEyeOpenProbability,
                        "right_eye_open_probability" to face.rightEyeOpenProbability,
                        "head_euler_angle_y" to face.headEulerAngleY,
                        "head_euler_angle_z" to face.headEulerAngleZ,
                    )
                }
                ToolResult.success(mapOf(
                    "face_count" to faces.size,
                    "faces" to faces,
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Face detection failed: ${e.message}")
        }
    }
}
