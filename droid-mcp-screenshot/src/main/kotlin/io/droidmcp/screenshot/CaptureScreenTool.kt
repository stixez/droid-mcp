package io.droidmcp.screenshot

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures the device screen via MediaProjection and saves it as a PNG or JPEG.
 *
 * Mirrors the display into an [android.media.ImageReader] through a
 * [android.hardware.display.VirtualDisplay] owned by [MediaProjectionHolder] — created once per
 * projection (Android 14+ allows only one `createVirtualDisplay` per projection) and reused by
 * every call — and encodes the newest frame. Requires an active MediaProjection: the host app
 * must obtain user consent and register the projection token via [MediaProjectionHolder]; if
 * absent, returns an error. Destructive hint (it writes an image). On API 34+ the host must run
 * a `mediaProjection` foreground service (manifest declares
 * `FOREGROUND_SERVICE_MEDIA_PROJECTION`).
 *
 * **Storage.** The image is inserted through `MediaStore` into `Pictures/droid-mcp` (using
 * `IS_PENDING` on API 29+, so no storage permission is needed there). On API 28 the insert
 * needs `WRITE_EXTERNAL_STORAGE`; without it the image is written to the app's private cache
 * instead (`saved_to = "cache"`).
 *
 * Output keys: `success`, `path` (the image's filesystem path when MediaStore reports one, else
 * the content URI; for cache saves the private file path), `uri` (content URI, or `file://` URI
 * for cache saves), `saved_to` (`gallery` or `cache`), `width`, `height`, `format`,
 * `size_bytes`.
 */
class CaptureScreenTool(private val context: Context) : McpTool {

    override val name = "capture_screen"
    override val description = "Capture a screenshot of the current screen. Requires MediaProjection consent from the host app. Saves the image (PNG by default, or JPEG) to the gallery (Pictures/droid-mcp) and returns its path / content URI."
    override val parameters = listOf(
        ToolParameter("quality", "JPEG quality 1-100 (default: 90). Only used if format is 'jpeg'.", ParameterType.INTEGER),
        ToolParameter("format", "Image format: 'png' (default) or 'jpeg'", ParameterType.STRING),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (MediaProjectionHolder.projection == null) {
            return ToolResult.error("MediaProjection not available. The host app must grant screen capture consent first.")
        }

        val format = params["format"]?.toString() ?: "png"
        if (format !in listOf("png", "jpeg")) {
            return ToolResult.error("format must be 'png' or 'jpeg'")
        }
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 90

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        return try {
            val capture = MediaProjectionHolder.captureFor(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
                ?: return ToolResult.error("MediaProjection not available. The host app must grant screen capture consent first.")

            // The first capture after the virtual display is created (or resized) must wait
            // for a frame to render; later captures usually have one immediately.
            var bitmap: Bitmap? = null
            val deadline = System.currentTimeMillis() + FRAME_WAIT_MS
            while (bitmap == null) {
                bitmap = capture.withLatestFrame { image -> imageToBitmap(image, image.width, image.height) }
                if (bitmap != null || System.currentTimeMillis() >= deadline) break
                delay(FRAME_POLL_MS)
            }
            if (bitmap == null) return ToolResult.error("Failed to capture screen image")

            val width = bitmap.width
            val height = bitmap.height
            val saved = try {
                saveBitmap(bitmap, format, quality)
            } finally {
                bitmap.recycle()
            }

            ToolResult.success(mapOf(
                "success" to true,
                "path" to saved.path,
                "uri" to saved.uri,
                "saved_to" to saved.location,
                "width" to width,
                "height" to height,
                "format" to format,
                "size_bytes" to saved.sizeBytes,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to capture screenshot: ${e.message}")
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
            if (cropped !== padded) padded.recycle()
            cropped
        } else {
            padded
        }
    }

    private class SavedImage(val path: String, val uri: String, val location: String, val sizeBytes: Long)

    private fun saveBitmap(bitmap: Bitmap, format: String, quality: Int): SavedImage {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = if (format == "jpeg") "jpg" else "png"
        val mime = if (format == "jpeg") "image/jpeg" else "image/png"
        val displayName = "screenshot_$timestamp.$extension"
        val compressFormat = if (format == "jpeg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG

        val canUseMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!canUseMediaStore) {
            val dir = File(context.cacheDir, "droid-mcp-screenshots").apply { mkdirs() }
            val file = File(dir, displayName)
            FileOutputStream(file).use { out -> compressOrThrow(bitmap, compressFormat, quality, out) }
            return SavedImage(file.absolutePath, Uri.fromFile(file).toString(), "cache", file.length())
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/droid-mcp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry for screenshot")
        try {
            val counting = resolver.openOutputStream(uri)?.use { os ->
                CountingOutputStream(os).also { compressOrThrow(bitmap, compressFormat, quality, it) }
            } ?: throw IllegalStateException("Failed to open output stream for screenshot")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            val path = runCatching {
                @Suppress("DEPRECATION")
                resolver.query(uri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            return SavedImage(path ?: uri.toString(), uri.toString(), "gallery", counting.count)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun compressOrThrow(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, out: OutputStream) {
        if (!bitmap.compress(format, quality, out)) throw IllegalStateException("Bitmap encoding failed")
    }

    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count = 0L
            private set
        override fun write(b: Int) { delegate.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { delegate.write(b, off, len); count += len }
        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }

    private companion object {
        const val FRAME_WAIT_MS = 1_000L
        const val FRAME_POLL_MS = 50L
    }
}
