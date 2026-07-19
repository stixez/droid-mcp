package io.droidmcp.files

import android.content.Context
import io.droidmcp.core.*
import java.io.File

/**
 * Reads a text file's content, sandboxed to external storage via [PathValidator].
 * Files with an unrecognized extension are scanned for null bytes in the first 8 KB and
 * rejected as binary if any are found. Output is truncated to the `max_lines` param.
 * Requires `READ_EXTERNAL_STORAGE` on API ≤32; uses File API access on API 33+.
 * Output: `path`, `content` (newline-joined lines), `lines_returned`, `truncated`.
 */
class ReadFileTool(private val context: Context) : McpTool {

    override val name = "read_file"
    override val description = "Read the text content of a file. Only reads text files — returns an error for binary files. Large files are truncated to max_lines."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path to the file to read", ParameterType.STRING, required = true),
        ToolParameter("max_lines", "Maximum number of lines to return. Default 100.", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

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

        PathValidator.validate(path)?.let { return ToolResult.error(it) }

        val file = File(path)
        if (!file.exists()) return ToolResult.error("File does not exist: $path")
        if (!file.isFile) return ToolResult.error("Path is not a file: $path")
        if (!file.canRead()) return ToolResult.error("Cannot read file: $path — check READ_EXTERNAL_STORAGE or READ_MEDIA_* permissions")

        val extension = file.extension.lowercase()
        if (extension !in textExtensions) {
            // Heuristic binary check: scan first 8KB for null bytes. Applies to extensionless
            // files too — an unrecognized (missing) extension is exactly the "unrecognized
            // extension" case this check exists for.
            val header = file.inputStream().use { stream ->
                val buf = ByteArray(8192)
                val read = stream.read(buf)
                // read() returns -1 at EOF (e.g. a genuinely empty file) — take(-1) throws
                // IllegalArgumentException; there's simply no header to scan in that case.
                if (read <= 0) emptyList() else buf.take(read)
            }
            if (header.any { it == 0.toByte() }) {
                return ToolResult.error("File appears to be binary: $path — only text files can be read")
            }
        }

        val lines = mutableListOf<String>()
        var truncated = false
        try {
            file.bufferedReader().use { reader ->
                for (line in reader.lineSequence()) {
                    if (lines.size >= maxLines) {
                        truncated = true
                        break
                    }
                    lines.add(line)
                }
            }
        } catch (e: OutOfMemoryError) {
            // lineSequence() buffers a whole line before yielding it — one enormous line
            // (minified JSON, a single-line log) can exhaust the heap. OutOfMemoryError is an
            // Error, not an Exception, so it would otherwise escape uncaught and crash the host.
            return ToolResult.error("File contains an extremely long line and cannot be read as text: $path")
        }

        return ToolResult.success(mapOf(
            "path" to path,
            "content" to lines.joinToString("\n"),
            "lines_returned" to lines.size,
            "truncated" to truncated,
        ))
    }
}
