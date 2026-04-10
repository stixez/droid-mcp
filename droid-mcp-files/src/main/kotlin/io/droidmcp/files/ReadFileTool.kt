package io.droidmcp.files

import android.content.Context
import io.droidmcp.core.*
import java.io.File

class ReadFileTool(private val context: Context) : McpTool {

    override val name = "read_file"
    override val description = "Read the text content of a file. Only reads text files — returns an error for binary files. Large files are truncated to max_lines."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path to the file to read", ParameterType.STRING, required = true),
        ToolParameter("max_lines", "Maximum number of lines to return. Default 100.", ParameterType.INTEGER),
    )

    private val textMimeTypes = setOf(
        "text/", "application/json", "application/xml", "application/javascript",
        "application/x-sh", "application/x-yaml",
    )

    private val textExtensions = setOf(
        "txt", "md", "json", "xml", "csv", "log", "yaml", "yml", "toml", "ini",
        "conf", "cfg", "properties", "sh", "bat", "js", "ts", "kt", "java",
        "py", "rb", "go", "rs", "cpp", "c", "h", "html", "htm", "css",
        "gradle", "kts", "plist",
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString()
            ?: return ToolResult.error("path is required")
        val maxLines = (params["max_lines"] as? Number)?.toInt()?.coerceIn(1, 1000) ?: 100

        val file = File(path)
        if (!file.exists()) return ToolResult.error("File does not exist: $path")
        if (!file.isFile) return ToolResult.error("Path is not a file: $path")
        if (!file.canRead()) return ToolResult.error("Cannot read file: $path — check READ_EXTERNAL_STORAGE or READ_MEDIA_* permissions")

        val extension = file.extension.lowercase()
        if (extension.isNotEmpty() && extension !in textExtensions) {
            // Heuristic binary check: scan first 8KB for null bytes
            val header = file.inputStream().use { stream ->
                val buf = ByteArray(8192)
                val read = stream.read(buf)
                buf.take(read)
            }
            if (header.any { it == 0.toByte() }) {
                return ToolResult.error("File appears to be binary: $path — only text files can be read")
            }
        }

        val lines = mutableListOf<String>()
        var totalLines = 0
        file.bufferedReader().use { reader ->
            for (line in reader.lineSequence()) {
                totalLines++
                if (lines.size < maxLines) lines.add(line)
            }
        }

        val truncated = totalLines > maxLines
        return ToolResult.success(mapOf(
            "path" to path,
            "content" to lines.joinToString("\n"),
            "lines_returned" to lines.size,
            "total_lines" to totalLines,
            "truncated" to truncated,
        ))
    }
}
