package io.droidmcp.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureScreenTool(private val context: Context) : McpTool {

    override val name = "capture_screen"
    override val description = "Capture a screenshot of the current screen. Requires MediaProjection consent from the host app. Returns the file path to the saved PNG image."
    override val parameters = listOf(
        ToolParameter("quality", "JPEG quality 1-100 (default: 90). Only used if format is 'jpeg'.", ParameterType.INTEGER),
        ToolParameter("format", "Image format: 'png' (default) or 'jpeg'", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val projection = MediaProjectionHolder.projection
            ?: return ToolResult.error("MediaProjection not available. The host app must grant screen capture consent first.")

        val format = params["format"]?.toString() ?: "png"
        if (format !in listOf("png", "jpeg")) {
            return ToolResult.error("format must be 'png' or 'jpeg'")
        }
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 90

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        return try {
            virtualDisplay = projection.createVirtualDisplay(
                "droid-mcp-screenshot",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, null,
            )

            // Give the virtual display time to render a frame
            delay(300)

            val image: Image = imageReader.acquireLatestImage()
                ?: return ToolResult.error("Failed to capture screen image")

            val bitmap = imageToBitmap(image, width, height)
            image.close()

            val file = saveBitmap(bitmap, format, quality)
            bitmap.recycle()

            ToolResult.success(mapOf(
                "success" to true,
                "path" to file.absolutePath,
                "width" to width,
                "height" to height,
                "format" to format,
                "size_bytes" to file.length(),
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to capture screenshot: ${e.message}")
        } finally {
            virtualDisplay?.release()
            imageReader.close()
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else {
            bitmap
        }
    }

    private fun saveBitmap(bitmap: Bitmap, format: String, quality: Int): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = if (format == "jpeg") "jpg" else "png"
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "droid-mcp"
        )
        dir.mkdirs()
        val file = File(dir, "screenshot_$timestamp.$extension")

        FileOutputStream(file).use { out ->
            val compressFormat = if (format == "jpeg") {
                Bitmap.CompressFormat.JPEG
            } else {
                Bitmap.CompressFormat.PNG
            }
            bitmap.compress(compressFormat, quality, out)
        }

        return file
    }
}
