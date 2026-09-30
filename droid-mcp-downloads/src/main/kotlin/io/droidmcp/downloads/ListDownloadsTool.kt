package io.droidmcp.downloads

import android.content.Context
import android.os.Environment
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lists files (directories excluded) in the public Downloads directory, sortable by
 * `date` (default), `name`, or `size` via the `sort_by` param.
 * Requires `READ_EXTERNAL_STORAGE` on API ≤32; uses File API access on API 33+.
 *
 * Scoped-storage limitation: on API 30+ the File API only sees files this app created plus
 * media files (images/video/audio, when the host holds the matching media permission). Non-media
 * files saved by *other* apps (PDFs, APKs, archives downloaded by a browser, …) are invisible
 * and simply don't appear in the results — there is no permission this module requests that
 * changes that (only `MANAGE_EXTERNAL_STORAGE`, granted by the user in Settings, lifts it).
 * Output: `files` (each `{name, size_bytes, last_modified, extension}`) capped at the
 * `limit` param, plus `count`, `sort_by`, and `directory`.
 */
class ListDownloadsTool(private val context: Context) : McpTool {

    override val name = "list_downloads"
    override val description = "List files in the Downloads directory. On Android 11+ scoped storage hides non-media files created by other apps."
    override val parameters = listOf(
        ToolParameter("limit", "Maximum number of files to return (1-100, default: 10)", ParameterType.INTEGER),
        ToolParameter("sort_by", "Sort order: date, name, size (default: date)", ParameterType.STRING),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10
        val sortBy = params["sort_by"]?.toString() ?: "date"

        // SimpleDateFormat isn't thread-safe; tool calls can run concurrently, so one per call.
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!downloadsDir.exists() || !downloadsDir.canRead()) {
            return ToolResult.error("Downloads directory is not accessible. Check storage permissions.")
        }

        val files = downloadsDir.listFiles()
            ?.filter { it.isFile }
            ?: return ToolResult.success(mapOf("files" to emptyList<Any>(), "count" to 0))

        val sorted = when (sortBy.lowercase()) {
            "name" -> files.sortedBy { it.name.lowercase() }
            "size" -> files.sortedByDescending { it.length() }
            else -> files.sortedByDescending { it.lastModified() } // date
        }

        val result = sorted.take(limit).map { file ->
            mapOf(
                "name" to file.name,
                "size_bytes" to file.length(),
                "last_modified" to dateFormat.format(Date(file.lastModified())),
                "extension" to (file.extension.takeIf { it.isNotEmpty() } ?: ""),
            )
        }

        return ToolResult.success(mapOf(
            "files" to result,
            "count" to result.size,
            "sort_by" to sortBy,
            "directory" to downloadsDir.absolutePath,
        ))
    }
}
