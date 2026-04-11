package io.droidmcp.files

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object FilesTools {

    fun all(context: Context): List<McpTool> = listOf(
        BrowseFilesTool(context),
        ReadFileTool(context),
        SearchFilesTool(context),
    )

    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // On API 33+, File API access to shared storage works without
            // media permissions. Only legacy READ_EXTERNAL_STORAGE needed on older APIs.
            emptyList()
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
