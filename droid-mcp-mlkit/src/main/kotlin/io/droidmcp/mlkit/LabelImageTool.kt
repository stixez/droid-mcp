package io.droidmcp.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.io.File

class LabelImageTool(private val context: Context) : McpTool {

    override val name = "label_image"
    override val description = "Classify the contents of an image using ML Kit image labeling"
    override val parameters = listOf(
        ToolParameter("image_path", "Absolute path to an image under external storage", ParameterType.STRING, required = true),
        ToolParameter("min_confidence", "Minimum confidence threshold 0.0-1.0 (default 0.5)", ParameterType.NUMBER, required = false),
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
        val threshold = (params["min_confidence"] as? Number)?.toFloat()
            ?.coerceIn(0f, 1f) ?: 0.5f

        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(threshold)
                .build()
            val labeler = ImageLabeling.getClient(options)
            val labels = labeler.process(image).awaitResult().map { label ->
                mapOf(
                    "text" to label.text,
                    "confidence" to label.confidence,
                    "index" to label.index,
                )
            }
            ToolResult.success(mapOf(
                "label_count" to labels.size,
                "labels" to labels,
            ))
        } catch (e: Exception) {
            ToolResult.error("Image labeling failed: ${e.message}")
        }
    }
}
