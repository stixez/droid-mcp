package io.droidmcp.downloads

import android.content.Context
import android.os.Environment
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ListDownloadsTool(private val context: Context) : McpTool {

    override val name = "list_downloads"
    override val description = "List files in the Downloads directory"
    override val parameters = listOf(
        ToolParameter("limit", "Maximum number of files to return (1-100, default: 20)", ParameterType.INTEGER),
        ToolParameter("sort_by", "Sort order: date, name, size (default: date)", ParameterType.STRING),
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val sortBy = params["sort_by"]?.toString() ?: "date"

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
