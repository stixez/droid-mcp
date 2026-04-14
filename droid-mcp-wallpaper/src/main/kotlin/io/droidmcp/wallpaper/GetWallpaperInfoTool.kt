package io.droidmcp.wallpaper

import android.app.WallpaperManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetWallpaperInfoTool(private val context: Context) : McpTool {

    override val name = "get_wallpaper_info"
    override val description = "Get information about the current wallpaper including dimensions and whether a live wallpaper is active"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val wm = WallpaperManager.getInstance(context)

        return ToolResult.success(mapOf(
            "desired_width" to wm.desiredMinimumWidth,
            "desired_height" to wm.desiredMinimumHeight,
            "is_live_wallpaper" to (wm.wallpaperInfo != null),
            "live_wallpaper_package" to wm.wallpaperInfo?.packageName,
            "live_wallpaper_service" to wm.wallpaperInfo?.serviceName,
            "is_set_wallpaper_allowed" to wm.isSetWallpaperAllowed,
        ))
    }
}
