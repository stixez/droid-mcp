package io.droidmcp.downloads

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the Downloads-directory tools ([ListDownloadsTool], [SearchDownloadsTool]).
 */
object DownloadsTools {

    /** All downloads tools, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ListDownloadsTool(context),
        SearchDownloadsTool(context),
    )

    /** `READ_EXTERNAL_STORAGE` on API ≤32; empty on API 33+ where File API access needs no media permission. */
    fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // On API 33+, File API access to Downloads works without media permissions
        emptyList()
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Whether [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
