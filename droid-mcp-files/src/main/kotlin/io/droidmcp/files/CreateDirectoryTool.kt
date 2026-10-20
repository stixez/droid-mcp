package io.droidmcp.files

import io.droidmcp.core.*
import java.io.File

/**
 * Creates a directory (and any missing parents) under external storage, sandboxed via
 * [PathValidator]. Succeeds without change when it already exists.
 * Output: `path`, `created` (false when it was already there).
 */
class CreateDirectoryTool : McpTool {

    override val name = "create_directory"
    override val description = "Create a directory, including missing parent directories, on shared storage. Does nothing if it already exists."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path of the directory to create", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        string("path")
        boolean("created")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString() ?: return ToolResult.error("path is required")
        PathValidator.validate(path)?.let { return ToolResult.error(it) }
        if (PathValidator.isInAndroidDir(path)) return ToolResult.error("Refusing to create directories under Android/: $path")

        val dir = File(path)
        if (dir.isDirectory) return ToolResult.success(mapOf("path" to dir.absolutePath, "created" to false))
        if (dir.exists()) return ToolResult.error("A file already exists at $path")
        if (!dir.mkdirs()) return ToolResult.error("Could not create directory: $path — on Android 11+ most folders outside Download/ and Documents/ are read-only")
        return ToolResult.success(mapOf("path" to dir.absolutePath, "created" to true))
    }
}
