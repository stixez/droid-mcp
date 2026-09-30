package io.droidmcp.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.io.File

/**
 * Sets the wallpaper from a local image file via [WallpaperManager.setBitmap], targeting the home
 * screen, lock screen, or both.
 *
 * Requires the `SET_WALLPAPER` permission (normal, granted at install). The `path` is sandboxed to
 * the external-storage root (canonical-path check); paths outside it are rejected. Also rejected if
 * device policy disallows setting wallpaper ([WallpaperManager.isSetWallpaperAllowed]) or the image
 * cannot be read/decoded (the error mentions scoped-storage read access as the usual cause).
 *
 * The image is decoded from the validated canonical path in two passes: bounds first, then with a
 * power-of-two `inSampleSize` that brings it down to roughly
 * [WallpaperManager.getDesiredMinimumWidth]/[WallpaperManager.getDesiredMinimumHeight] (falling
 * back to the display size), so large photos don't exhaust memory. A `setBitmap` return of 0 is
 * reported as a failure.
 *
 * Output keys: `success`, `path`, `target`, `width`/`height` (the decoded, possibly downsampled
 * bitmap), `original_width`, `original_height`, `sample_size`.
 */
class SetWallpaperTool(private val context: Context) : McpTool {

    override val name = "set_wallpaper"
    override val description = "Set the wallpaper from an image file. Supports setting for home screen, lock screen, or both."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path to the image file", ParameterType.STRING, required = true),
        ToolParameter("target", "Where to set: 'home', 'lock', or 'both' (default: 'both')", ParameterType.STRING),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString()
            ?: return ToolResult.error("path is required")
        val target = params["target"]?.toString() ?: "both"

        if (target !in listOf("home", "lock", "both")) {
            return ToolResult.error("target must be 'home', 'lock', or 'both'")
        }

        // Sandbox to external storage, matching PathValidator pattern from file tools
        val canonical = File(path).canonicalPath
        val externalRoot = Environment.getExternalStorageDirectory().canonicalPath
        if (canonical != externalRoot && !canonical.startsWith(externalRoot + File.separator)) {
            return ToolResult.error("Access denied: path must be within external storage")
        }

        // Use the canonical (validated) path from here on, so a symlink swapped after the check
        // can't redirect the decode outside the sandbox.
        val file = File(canonical)
        if (!file.exists()) {
            return ToolResult.error("File not found: $path")
        }
        if (!file.canRead()) {
            return ToolResult.error(readErrorHint(path))
        }

        val wm = WallpaperManager.getInstance(context)

        if (!wm.isSetWallpaperAllowed) {
            return ToolResult.error("Setting wallpaper is not allowed by device policy")
        }

        // Pass 1: bounds only, so a huge image isn't fully decoded just to be scaled down.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(canonical, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return ToolResult.error("Failed to decode image file: $path. ${readErrorHint(null)}")
        }

        val metrics = context.resources.displayMetrics
        val reqWidth = wm.desiredMinimumWidth.takeIf { it > 0 } ?: metrics.widthPixels
        val reqHeight = wm.desiredMinimumHeight.takeIf { it > 0 } ?: metrics.heightPixels
        val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)

        // Pass 2: downsampled decode.
        val bitmap = try {
            BitmapFactory.decodeFile(canonical, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } catch (e: OutOfMemoryError) {
            return ToolResult.error("Image too large to decode: $path")
        } ?: return ToolResult.error("Failed to decode image file: $path. ${readErrorHint(null)}")

        return try {
            val which = when (target) {
                "home" -> WallpaperManager.FLAG_SYSTEM
                "lock" -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }

            val wallpaperId = wm.setBitmap(bitmap, null, true, which)
            if (wallpaperId == 0) {
                return ToolResult.error("Failed to set wallpaper: the system rejected the image")
            }

            ToolResult.success(mapOf(
                "success" to true,
                "path" to path,
                "target" to target,
                "width" to bitmap.width,
                "height" to bitmap.height,
                "original_width" to bounds.outWidth,
                "original_height" to bounds.outHeight,
                "sample_size" to sampleSize,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to set wallpaper: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }

    /** Explains the usual reasons a path under external storage can't be read. */
    private fun readErrorHint(path: String?): String =
        (if (path != null) "Cannot read $path. " else "") +
            "The file may not be a supported image, or the app lacks read access: under scoped " +
            "storage (Android 10+) apps can only read other apps' images with READ_MEDIA_IMAGES " +
            "(API 33+) / READ_EXTERNAL_STORAGE, or MANAGE_EXTERNAL_STORAGE for non-media files."

    /**
     * Largest power-of-two sample size that keeps the decoded image at least [reqWidth]x[reqHeight]
     * (compared orientation-independently, long side vs long side).
     */
    private fun computeSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        val imgLong = maxOf(width, height)
        val imgShort = minOf(width, height)
        val reqLong = maxOf(reqWidth, reqHeight).coerceAtLeast(1)
        val reqShort = minOf(reqWidth, reqHeight).coerceAtLeast(1)
        var sample = 1
        while (imgLong / (sample * 2) >= reqLong && imgShort / (sample * 2) >= reqShort) {
            sample *= 2
        }
        return sample
    }
}
