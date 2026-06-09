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
 * Searches the public Downloads directory (top level only, files excluding directories)
 * for filenames containing a case-insensitive substring, sorted newest-first.
 * Requires `READ_EXTERNAL_STORAGE` on API ≤32; uses File API access on API 33+.
 * Output: `files` (each `{name, size_bytes, last_modified, extension}`) capped at the
 * `limit` param, plus `count` and `query`.
 */
class SearchDownloadsTool(private val context: Context) : McpTool {

    override val name = "search_downloads"
    override val description = "Search files in the Downloads directory by filename"
    override val parameters = listOf(
        ToolParameter("query", "Search query to match against filenames (case-insensitive)", ParameterType.STRING, required = true),
        ToolParameter("limit", "Maximum number of results to return (1-100, default: 10)", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!downloadsDir.exists() || !downloadsDir.canRead()) {
            return ToolResult.error("Downloads directory is not accessible. Check storage permissions.")
        }

        val files = downloadsDir.listFiles()
            ?.filter { it.isFile && it.name.contains(query, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?: emptyList()

        val result = files.map { file ->
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
            "query" to query,
        ))
    }
}
