package io.droidmcp.files

import android.content.Context
import io.droidmcp.core.*
import java.io.File

/**
 * Reads a text file's content, sandboxed to external storage via [PathValidator].
 * Files with an unrecognized extension are scanned for null bytes in the first 8 KB and
 * rejected as binary if any are found. Output is truncated to the `max_lines` param and to a
 * hard cap of ~1 MB of characters ([MAX_CHARS]); the file is streamed through a bounded
 * buffer, so arbitrarily large files or single huge lines never load fully into memory.
 * Requires `READ_EXTERNAL_STORAGE` on API ≤32; uses File API access on API 33+. Under scoped
 * storage (API 30+) non-media files created by other apps are not readable.
 * Output: `path`, `content` (newline-joined lines), `lines_returned`, `truncated`, and
 * `truncated_by` (`"max_lines"`, `"max_chars"`, or null).
 */
class ReadFileTool(private val context: Context) : McpTool {

    override val name = "read_file"
    override val description = "Read the text content of a file. Only reads text files — returns an error for binary files. Large files are truncated to max_lines (and at most ~1M characters). On Android 11+ non-media files created by other apps are not visible."
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
        if (!file.canRead()) return ToolResult.error("Cannot read file: $path — check READ_EXTERNAL_STORAGE (API ≤32); on Android 11+ scoped storage also hides other apps' non-media files")

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

        val read = try {
            readBounded(file, maxLines)
        } catch (e: Exception) {
            return ToolResult.error("Failed to read file: $path — ${e.message}")
        }

        return ToolResult.success(mapOf(
            "path" to path,
            "content" to read.content,
            "lines_returned" to read.lines,
            "truncated" to (read.truncatedBy != null),
            "truncated_by" to read.truncatedBy,
        ))
    }

    private class BoundedRead(val content: String, val lines: Int, val truncatedBy: String?)

    /**
     * Streams [file] through a fixed-size char buffer, normalizing `\n`, `\r\n` and `\r` line
     * breaks to `\n`, and stops at [maxLines] lines or [MAX_CHARS] characters, whichever comes
     * first. Unlike `lineSequence()` it never materializes a whole line, so a single enormous line
     * (minified JSON, one-line logs) or a multi-GB file costs at most [MAX_CHARS] of heap.
     */
    private fun readBounded(file: File, maxLines: Int): BoundedRead {
        val content = StringBuilder()
        var breaks = 0
        var partial = false
        var prevCr = false
        var truncatedBy: String? = null
        file.bufferedReader().use { reader ->
            val buf = CharArray(8192)
            var stopped = false
            // After hitting max_lines, we keep peeking only to learn whether anything follows.
            var peeking = false
            read@ while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                for (i in 0 until n) {
                    val c = buf[i]
                    if (prevCr && c == '\n') {
                        prevCr = false
                        continue
                    }
                    prevCr = false
                    if (peeking) {
                        truncatedBy = "max_lines"
                        stopped = true
                        break@read
                    }
                    if (c == '\n' || c == '\r') {
                        breaks++
                        partial = false
                        prevCr = c == '\r'
                        if (breaks >= maxLines) {
                            peeking = true
                        } else {
                            content.append('\n')
                        }
                        continue
                    }
                    if (content.length >= MAX_CHARS) {
                        truncatedBy = "max_chars"
                        stopped = true
                        break@read
                    }
                    content.append(c)
                    partial = true
                }
            }
            if (!stopped && !peeking && !partial && breaks > 0 && content.endsWith('\n')) {
                // A final trailing line break doesn't start a new (empty) line — matches
                // lineSequence(), which yields "a" (one line) for "a\n".
                content.setLength(content.length - 1)
            }
        }
        val lines = breaks.coerceAtMost(maxLines) + if (partial && breaks < maxLines) 1 else 0
        return BoundedRead(content.toString(), lines, truncatedBy)
    }

    private companion object {
        /** Hard cap on returned characters (~1 MB of chars), independent of `max_lines`. */
        const val MAX_CHARS = 1_048_576
    }
}
