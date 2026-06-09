package io.droidmcp.files

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the sandboxed file tools ([BrowseFilesTool], [ReadFileTool],
 * [SearchFilesTool]). All paths are confined to external storage by [PathValidator].
 */
object FilesTools {

    /** All file tools, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        BrowseFilesTool(context),
        ReadFileTool(context),
        SearchFilesTool(context),
    )

    /** `READ_EXTERNAL_STORAGE` on API ≤32; empty on API 33+ where File API access needs no media permission. */
    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // On API 33+, File API access to shared storage works without
            // media permissions. Only legacy READ_EXTERNAL_STORAGE needed on older APIs.
            emptyList()
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Whether [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
