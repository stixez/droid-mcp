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
import java.io.File

class RecognizeTextTool(private val context: Context) : McpTool {

    override val name = "recognize_text"
    override val description = "Extract text from an image on device using ML Kit text recognition"
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
