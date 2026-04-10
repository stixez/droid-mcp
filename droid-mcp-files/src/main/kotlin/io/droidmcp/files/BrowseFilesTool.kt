package io.droidmcp.files

import android.content.Context
import io.droidmcp.core.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class BrowseFilesTool(private val context: Context) : McpTool {

    override val name = "browse_files"
    override val description = "List files and directories at the given path. Returns file name, size, last modified date, and whether each entry is a directory."
    override val parameters = listOf(
        ToolParameter("path", "Directory path to browse. Default: /sdcard", ParameterType.STRING),
        ToolParameter("limit", "Max number of entries to return. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString() ?: "/sdcard"
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        val dir = File(path)
        if (!dir.exists()) return ToolResult.error("Path does not exist: $path")
        if (!dir.isDirectory) return ToolResult.error("Path is not a directory: $path")
        if (!dir.canRead()) return ToolResult.error("Cannot read directory: $path — check READ_EXTERNAL_STORAGE or READ_MEDIA_* permissions")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val entries = dir.listFiles()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
            ?.take(limit)
            ?.map { file ->
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "size_bytes" to if (file.isFile) file.length() else null,
                    "last_modified" to dateFormat.format(Date(file.lastModified())),
                    "is_directory" to file.isDirectory,
                )
            }
            ?: return ToolResult.error("Could not list directory contents")

        return ToolResult.success(mapOf(
            "path" to path,
            "entries" to entries,
            "count" to entries.size,
        ))
    }
}
