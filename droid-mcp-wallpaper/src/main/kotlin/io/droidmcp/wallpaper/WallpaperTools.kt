package io.droidmcp.wallpaper

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object WallpaperTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetWallpaperInfoTool(context),
        SetWallpaperTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.SET_WALLPAPER,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
