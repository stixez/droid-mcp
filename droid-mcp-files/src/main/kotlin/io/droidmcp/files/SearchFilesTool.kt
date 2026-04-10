package io.droidmcp.files

import android.content.Context
import io.droidmcp.core.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SearchFilesTool(private val context: Context) : McpTool {

    override val name = "search_files"
    override val description = "Search for files by name pattern (case-insensitive substring match) under a given directory. Returns matching file paths with metadata."
    override val parameters = listOf(
        ToolParameter("query", "Filename pattern to search for (case-insensitive substring)", ParameterType.STRING, required = true),
        ToolParameter("path", "Root directory to search in. Default: /sdcard", ParameterType.STRING),
        ToolParameter("limit", "Max number of results to return. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val path = params["path"]?.toString() ?: "/sdcard"
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        PathValidator.validate(path)?.let { return ToolResult.error(it) }

        val root = File(path)
        if (!root.exists()) return ToolResult.error("Path does not exist: $path")
        if (!root.isDirectory) return ToolResult.error("Path is not a directory: $path")
        if (!root.canRead()) return ToolResult.error("Cannot read directory: $path — check READ_EXTERNAL_STORAGE or READ_MEDIA_* permissions")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val results = mutableListOf<Map<String, Any?>>()
        val queryLower = query.lowercase()

        fun searchDir(dir: File, currentDepth: Int, maxDepth: Int) {
            if (currentDepth > maxDepth) return
            if (results.size >= limit) return
            try {
                dir.listFiles()?.forEach { file ->
                    if (results.size >= limit) return
                    if (file.name.lowercase().contains(queryLower)) {
                        results.add(mapOf(
                            "name" to file.name,
                            "path" to file.absolutePath,
                            "size_bytes" to if (file.isFile) file.length() else null,
                            "last_modified" to dateFormat.format(Date(file.lastModified())),
                            "is_directory" to file.isDirectory,
                        ))
                    }
                    if (file.isDirectory && file.canRead()) {
                        searchDir(file, currentDepth + 1, maxDepth)
                    }
                }
            } catch (_: SecurityException) {
                // Skip directories we can't access
            }
        }

        searchDir(root, currentDepth = 0, maxDepth = 5)

        return ToolResult.success(mapOf(
            "query" to query,
            "search_path" to path,
            "results" to results,
            "count" to results.size,
        ))
    }
}
