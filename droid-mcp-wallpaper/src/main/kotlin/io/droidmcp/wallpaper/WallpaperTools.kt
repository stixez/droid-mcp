package io.droidmcp.wallpaper

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the wallpaper module: [GetWallpaperInfoTool] and [SetWallpaperTool].
 *
 * Declares `SET_WALLPAPER`, which is needed only by [SetWallpaperTool]; reading info needs nothing.
 */
object WallpaperTools {

    /** All wallpaper tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetWallpaperInfoTool(context),
        SetWallpaperTool(context),
    )

    /** `SET_WALLPAPER` — required by [SetWallpaperTool]. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.SET_WALLPAPER,
    )

    /** True when `SET_WALLPAPER` is held. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
