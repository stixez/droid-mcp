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
import java.io.File

class DetectFacesTool(private val context: Context) : McpTool {

    override val name = "detect_faces"
    override val description = "Detect faces in an image using ML Kit. Returns bounding boxes and expression probabilities. Does NOT perform identification."
    override val parameters = listOf(
        ToolParameter("image_path", "Absolute path to an image under external storage", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["image_path"]?.toString()
            ?: return ToolResult.error("image_path is required")
        if (!PathValidator.isAllowed(path)) {
            return ToolResult.error("Access denied: image_path is outside allowed storage directories")
        }
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return ToolResult.error("Image file not found: $path")
        }

        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
            val detector = FaceDetection.getClient(options)
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
        } catch (e: Exception) {
            ToolResult.error("Face detection failed: ${e.message}")
        }
    }
}
