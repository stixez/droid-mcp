package io.droidmcp.files

import io.droidmcp.core.*
import java.io.File

/**
 * Deletes a file or directory under external storage, sandboxed via [PathValidator]. The storage
 * root, its top-level folders (DCIM, Download, …) and anything under `Android/` are refused. A non-empty directory is only
 * removed with `recursive`, and then at most [MAX_RECURSIVE_ENTRIES] entries.
 * Output: `path`, `deleted_count` (files and directories removed).
 */
class DeleteFileTool : McpTool {

    override val name = "delete_file"
    override val description = "Delete a file, or a folder with recursive=true, on shared storage. The storage root, top-level folders (DCIM, Download, …) and Android/ are protected. Cannot be undone."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path of the file or directory to delete", ParameterType.STRING, required = true),
        ToolParameter("recursive", "Delete a non-empty directory and everything in it. Default false.", ParameterType.BOOLEAN),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        string("path")
        integer("deleted_count")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString() ?: return ToolResult.error("path is required")
        val recursive = params["recursive"] as? Boolean ?: false

        PathValidator.validate(path)?.let { return ToolResult.error(it) }
        if (PathValidator.isProtected(path)) {
            return ToolResult.error("Refusing to delete the storage root, a top-level folder or anything under Android/: $path")
        }
        val target = File(path)
        if (!target.exists()) return ToolResult.error("Path does not exist: $path")

        // A link is removed as a link; its target is left alone.
        if (PathValidator.isSymlink(target)) {
            if (!target.delete()) return ToolResult.error("Could not delete link $path")
            return ToolResult.success(mapOf("path" to target.absolutePath, "deleted_count" to 1))
        }

        if (target.isDirectory && !target.list().isNullOrEmpty()) {
            if (!recursive) return ToolResult.error("Directory is not empty: $path — set recursive to delete it and its contents")
            if (PathValidator.containsSymlink(target)) {
                return ToolResult.error("Directory contains symbolic links; refusing a recursive delete of $path")
            }
            val count = target.walkTopDown().take(MAX_RECURSIVE_ENTRIES + 1).count()
            if (count > MAX_RECURSIVE_ENTRIES) {
                return ToolResult.error("Directory has more than $MAX_RECURSIVE_ENTRIES entries; delete its contents in smaller steps")
            }
        }

        // Children first, so a partial failure reports how far it got.
        var deleted = 0
        for (file in target.walkBottomUp()) {
            if (!file.delete()) {
                return ToolResult.error("Could not delete ${file.path} after removing $deleted entries — on Android 11+ other apps' files are read-only")
            }
            deleted++
        }
        return ToolResult.success(mapOf("path" to target.absolutePath, "deleted_count" to deleted))
    }

}

/** Upper bound on a recursive delete, to keep a mistaken call from wiping a whole tree. */
private const val MAX_RECURSIVE_ENTRIES = 1000
