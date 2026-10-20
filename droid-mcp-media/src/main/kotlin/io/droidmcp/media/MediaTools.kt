package io.droidmcp.media

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper
import io.droidmcp.core.ResourceProvider

/**
 * Provider for the MediaStore tools ([SearchMediaTool], [GetMediaMetadataTool],
 * [ListAlbumsTool]) covering images and videos.
 */
object MediaTools {

    /** All media tools, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        SearchMediaTool(context),
        GetMediaMetadataTool(context),
        ListAlbumsTool(context),
    )

    /** Recent photos and videos as MCP resources; register with `DroidMcp.Builder.addResourceProvider`. */
    fun resources(context: Context): ResourceProvider = MediaResourceProvider(context)

    /** `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` on API 33+, else `READ_EXTERNAL_STORAGE`. */
    fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Whether [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
