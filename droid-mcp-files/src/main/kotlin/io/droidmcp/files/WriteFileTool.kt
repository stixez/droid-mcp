package io.droidmcp.files

import io.droidmcp.core.*
import java.io.File

/**
 * Writes UTF-8 text to a file, sandboxed to external storage via [PathValidator]. `mode` is
 * `create` (default; fails if the file exists), `overwrite` or `append`. Parent directories are
 * created only with `create_dirs`. Content is capped at [MAX_CHARS] characters. Nothing under
 * `Android/` (other apps' data) can be written.
 * On API 30+ only this app's own files and new files in shared folders such as Download/ or
 * Documents/ are writable; see [FilesTools] for API 28-29.
 * Output: `path`, `bytes_written`, `size_bytes` (file size afterwards), `created`.
 */
class WriteFileTool : McpTool {

    override val name = "write_file"
    override val description = "Write text to a file on shared storage. Creates a new file by default; set mode to 'overwrite' or 'append' to change an existing one. On Android 11+ only this app's files and new files in folders such as Download/ or Documents/ are writable."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path of the file to write", ParameterType.STRING, required = true),
        ToolParameter("content", "Text to write (UTF-8)", ParameterType.STRING, required = true),
        ToolParameter("mode", "create (fail if it exists, default), overwrite, or append", ParameterType.STRING, enumValues = listOf("create", "overwrite", "append")),
        ToolParameter("create_dirs", "Create missing parent directories. Default false.", ParameterType.BOOLEAN),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        string("path")
        integer("bytes_written")
        integer("size_bytes")
        boolean("created")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString() ?: return ToolResult.error("path is required")
        val content = params["content"]?.toString() ?: return ToolResult.error("content is required")
        val mode = params["mode"]?.toString()?.lowercase() ?: "create"
        if (mode !in setOf("create", "overwrite", "append")) {
            return ToolResult.error("Invalid mode '$mode'. Use: create, overwrite, append")
        }
        val createDirs = params["create_dirs"] as? Boolean ?: false
        if (content.length > MAX_CHARS) return ToolResult.error("content is too long (max $MAX_CHARS characters)")

        PathValidator.validate(path)?.let { return ToolResult.error(it) }
        if (PathValidator.isInAndroidDir(path)) return ToolResult.error("Refusing to write under Android/, which holds other apps' data: $path")
        val file = File(path)
        if (file.isDirectory) return ToolResult.error("Path is a directory: $path")
        val existed = file.exists()
        if (existed && mode == "create") return ToolResult.error("File already exists: $path — use mode 'overwrite' or 'append'")

        val parent = file.parentFile ?: return ToolResult.error("Path has no parent directory: $path")
        if (!parent.isDirectory) {
            if (!createDirs) return ToolResult.error("Parent directory does not exist: ${parent.path} — set create_dirs to create it")
            if (!parent.mkdirs() && !parent.isDirectory) return ToolResult.error("Could not create directory: ${parent.path}")
        }

        val bytes = content.toByteArray(Charsets.UTF_8)
        try {
            if (mode == "append") file.appendBytes(bytes) else file.writeBytes(bytes)
        } catch (e: Exception) {
            return ToolResult.error("Failed to write $path — ${e.message}. On Android 11+ other apps' files and most folders outside Download/ and Documents/ are read-only.")
        }
        return ToolResult.success(mapOf(
            "path" to file.absolutePath,
            "bytes_written" to bytes.size,
            "size_bytes" to file.length(),
            "created" to !existed,
        ))
    }

}

/** Largest accepted `content`, in characters. */
private const val MAX_CHARS = 1_048_576
