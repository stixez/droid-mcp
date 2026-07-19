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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Classifies the contents of a local image with on-device ML Kit image labeling, returning labels
 * above `min_confidence` (clamped 0.0–1.0, default 0.5).
 *
 * No permissions required; `image_path` is sandboxed to the external-storage root via
 * [PathValidator] and must point at an existing file. Read-only.
 *
 * Output keys: `label_count`, `labels` (each with `text`, `confidence`, `index`).
 */
class LabelImageTool(private val context: Context) : McpTool {

    override val name = "label_image"
    override val description = "Classify the contents of an image using ML Kit image labeling"
    override val parameters = listOf(
        ToolParameter("image_path", "Absolute path to an image under external storage", ParameterType.STRING, required = true),
        ToolParameter("min_confidence", "Minimum confidence threshold 0.0-1.0 (default 0.5)", ParameterType.NUMBER, required = false),
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
        val threshold = (params["min_confidence"] as? Number)?.toFloat()
            ?.coerceIn(0f, 1f) ?: 0.5f

        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(threshold)
                .build()
            ImageLabeling.getClient(options).use { labeler ->
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
            }
        } catch (e: Exception) {
            ToolResult.error("Image labeling failed: ${e.message}")
        }
    }
}
