package io.droidmcp.files

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper
import io.droidmcp.core.ResourceProvider

/**
 * Provider for the sandboxed file tools: [BrowseFilesTool], [ReadFileTool] and
 * [SearchFilesTool], plus the write tools [WriteFileTool], [CreateDirectoryTool], [MoveFileTool]
 * and [DeleteFileTool]. All paths are confined to external storage by [PathValidator].
 *
 * The write tools are opt-in (`all(context, includeWriteTools = true)`), and even then registered
 * only when the File API can actually write shared storage: always on
 * API 30+ (scoped storage decides what is writable), and on API 28-29 only once
 * `WRITE_EXTERNAL_STORAGE` is granted. This module declares that permission up to API 28; on
 * API 29 the host must declare it up to 29 itself and opt out of scoped storage
 * (`android:requestLegacyExternalStorage="true"`), since Android 10 blocks File-API writes to
 * shared folders otherwise.
 *
 * Scoped-storage limitation: on API 30+ the File API only exposes this app's own files and
 * shared media; non-media files (documents, archives, APKs) created by *other* apps are
 * invisible. This module declares no media permissions and does not request
 * `MANAGE_EXTERNAL_STORAGE` — a host that needs full shared-storage access must declare and
 * obtain it itself.
 */
object FilesTools {

    /**
     * File tools: the read tools always; the write tools only when [includeWriteTools] is set
     * (they can delete and move user files, so a host opts in explicitly) and [canWrite] holds.
     */
    @JvmOverloads
    fun all(context: Context, includeWriteTools: Boolean = false): List<McpTool> = buildList {
        add(BrowseFilesTool(context))
        add(ReadFileTool(context))
        add(SearchFilesTool(context))
        if (includeWriteTools && canWrite(context)) {
            add(WriteFileTool())
            add(CreateDirectoryTool())
            add(MoveFileTool())
            add(DeleteFileTool())
        }
    }

    /** Files under external storage as MCP resources; register with `DroidMcp.Builder.addResourceProvider`. */
    fun resources(): ResourceProvider = FilesResourceProvider()

    /** Whether the write tools can be registered; see the class docs for the per-API rules. */
    fun canWrite(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> true
        !PermissionHelper.hasPermissions(context, listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) -> false
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Environment.isExternalStorageLegacy()
        else -> true
    }

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
