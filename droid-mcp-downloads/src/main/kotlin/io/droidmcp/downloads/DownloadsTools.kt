package io.droidmcp.downloads

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object DownloadsTools {

    fun all(context: Context): List<McpTool> = listOf(
        ListDownloadsTool(context),
        SearchDownloadsTool(context),
    )

    fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
