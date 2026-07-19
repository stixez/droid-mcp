package io.droidmcp.mlkit

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts text from a local image with on-device ML Kit text recognition (Latin script). Returns
 * the full recognized text plus per-line text and bounding boxes.
 *
 * No permissions required; `image_path` is sandboxed to the external-storage root via
 * [PathValidator] and must point at an existing file. Read-only.
 *
 * Output keys: `text`, `line_count`, `lines` (each with `text` and nullable `bounding_box`
 * {`left`, `top`, `right`, `bottom`}).
 */
class RecognizeTextTool(private val context: Context) : McpTool {

    override val name = "recognize_text"
    override val description = "Extract text from an image on device using ML Kit text recognition"
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
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { recognizer ->
                val result = recognizer.process(image).awaitResult()
                val lines = result.textBlocks.flatMap { it.lines }.map { line ->
                    val box = line.boundingBox
                    mapOf(
                        "text" to line.text,
                        "bounding_box" to if (box != null) mapOf(
                            "left" to box.left,
                            "top" to box.top,
                            "right" to box.right,
                            "bottom" to box.bottom,
                        ) else null,
                    )
                }
                ToolResult.success(mapOf(
                    "text" to result.text,
                    "line_count" to lines.size,
                    "lines" to lines,
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Text recognition failed: ${e.message}")
        }
    }
}
