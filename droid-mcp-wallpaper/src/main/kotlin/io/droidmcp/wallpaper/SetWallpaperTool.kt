package io.droidmcp.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.io.File

class SetWallpaperTool(private val context: Context) : McpTool {

    override val name = "set_wallpaper"
    override val description = "Set the wallpaper from an image file. Supports setting for home screen, lock screen, or both."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path to the image file", ParameterType.STRING, required = true),
        ToolParameter("target", "Where to set: 'home', 'lock', or 'both' (default: 'both')", ParameterType.STRING),
    )

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
        if (!canonical.startsWith(externalRoot)) {
            return ToolResult.error("Access denied: path must be within external storage")
        }

        val file = File(path)
        if (!file.exists()) {
            return ToolResult.error("File not found: $path")
        }

        val wm = WallpaperManager.getInstance(context)

        if (!wm.isSetWallpaperAllowed) {
            return ToolResult.error("Setting wallpaper is not allowed by device policy")
        }

        val bitmap = BitmapFactory.decodeFile(path)
            ?: return ToolResult.error("Failed to decode image file: $path")

        return try {
            val which = when (target) {
                "home" -> WallpaperManager.FLAG_SYSTEM
                "lock" -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }

            wm.setBitmap(bitmap, null, true, which)

            ToolResult.success(mapOf(
                "success" to true,
                "path" to path,
                "target" to target,
                "width" to bitmap.width,
                "height" to bitmap.height,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to set wallpaper: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }
}
