package io.droidmcp.files

import io.droidmcp.core.*
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Moves or renames a file or directory; both ends must pass [PathValidator]. The storage root, its
 * top-level folders (DCIM, Download, …) and `Android/` can't be moved, matching what
 * [DeleteFileTool] refuses, and nothing can be moved into `Android/`.
 * An existing destination is replaced only with `overwrite` (never when it is a non-empty directory,
 * the storage root or a top-level folder); it is set aside first and restored if the move fails, so
 * a failed move loses nothing. A rename that only changes letter case works in place. Falls back to copy-then-delete when a plain rename fails (e.g. across volumes).
 * Output: `source`, `destination`, `is_directory`.
 */
class MoveFileTool : McpTool {

    override val name = "move_file"
    override val description = "Move or rename a file or directory on shared storage. Fails if the destination exists unless overwrite is true."
    override val parameters = listOf(
        ToolParameter("source", "Absolute path of the file or directory to move", ParameterType.STRING, required = true),
        ToolParameter("destination", "Absolute path to move it to (including the new name)", ParameterType.STRING, required = true),
        ToolParameter("overwrite", "Replace an existing destination file. Default false.", ParameterType.BOOLEAN),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        string("source")
        string("destination")
        boolean("is_directory")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val sourcePath = params["source"]?.toString() ?: return ToolResult.error("source is required")
        val destinationPath = params["destination"]?.toString() ?: return ToolResult.error("destination is required")
        val overwrite = params["overwrite"] as? Boolean ?: false

        PathValidator.validate(sourcePath)?.let { return ToolResult.error(it) }
        PathValidator.validate(destinationPath)?.let { return ToolResult.error(it) }
        if (PathValidator.isProtected(sourcePath)) {
            return ToolResult.error("Refusing to move the storage root, a top-level folder or anything under Android/: $sourcePath")
        }
        if (PathValidator.isInAndroidDir(destinationPath)) {
            return ToolResult.error("Refusing to move into Android/, which holds other apps' data: $destinationPath")
        }

        val source = File(sourcePath)
        val destination = File(destinationPath)
        if (!source.exists()) return ToolResult.error("Source does not exist: $sourcePath")
        val parent = destination.parentFile
        if (parent == null || !parent.isDirectory) return ToolResult.error("Destination directory does not exist: ${parent?.path}")
        val isDirectory = source.isDirectory

        // Shared storage is case-insensitive: IMG.JPG -> IMG.jpg names the same file (and may even
        // share a canonical path), so it is a rename in place through a temporary name.
        if (source.name != destination.name && source.name.equals(destination.name, ignoreCase = true) &&
            destination.exists() && isSameFile(source, destination)
        ) {
            val temp = File(parent, ".${source.name}.droidmcp-rename-${UUID.randomUUID()}")
            if (!source.renameTo(temp)) return ToolResult.error("Could not rename $sourcePath")
            if (!temp.renameTo(destination)) {
                temp.renameTo(source)
                return ToolResult.error("Could not rename $sourcePath to ${destination.name}")
            }
            return success(source, destination, isDirectory)
        }

        if (source.canonicalPath == destination.canonicalPath) return ToolResult.error("Source and destination are the same")
        if (source.isDirectory && destination.canonicalPath.startsWith(source.canonicalPath + File.separator)) {
            return ToolResult.error("Can't move a directory into itself")
        }

        // An existing destination is renamed aside (same directory, so no copy) until the move succeeds.
        var setAside: File? = null
        if (destination.exists()) {
            if (!overwrite) return ToolResult.error("Destination already exists: $destinationPath — set overwrite to replace it")
            if (PathValidator.isProtected(destinationPath)) {
                return ToolResult.error("Refusing to replace the storage root or a top-level folder: $destinationPath")
            }
            if (destination.isDirectory && !destination.list().isNullOrEmpty()) {
                return ToolResult.error("Destination is a non-empty directory: $destinationPath")
            }
            val aside = File(parent, ".${destination.name}.droidmcp-replaced-${UUID.randomUUID()}")
            if (!destination.renameTo(aside)) return ToolResult.error("Could not replace $destinationPath")
            setAside = aside
        }

        val outcome = if (source.renameTo(destination)) CopyOutcome.MOVED else copyThenDelete(source, destination)
        return when (outcome) {
            CopyOutcome.MOVED -> {
                setAside?.delete()
                success(source, destination, isDirectory)
            }
            CopyOutcome.FAILED -> {
                setAside?.renameTo(destination)
                ToolResult.error("Could not move $sourcePath — on Android 11+ other apps' files are read-only")
            }
            CopyOutcome.SOURCE_LEFT -> {
                setAside?.delete()
                ToolResult.error(
                    "Copied $sourcePath to $destinationPath but could not remove all of the original — " +
                        "both now exist; on Android 11+ other apps' files are read-only",
                )
            }
        }
    }

    private fun success(source: File, destination: File, isDirectory: Boolean) = ToolResult.success(mapOf(
        "source" to source.absolutePath,
        "destination" to destination.absolutePath,
        "is_directory" to isDirectory,
    ))

    private fun isSameFile(a: File, b: File): Boolean = try {
        Files.isSameFile(a.toPath(), b.toPath())
    } catch (_: Exception) {
        false
    }

    private enum class CopyOutcome { MOVED, FAILED, SOURCE_LEFT }

    /**
     * Cross-volume fallback. Skipped for trees with links, which the copy would follow out of the
     * sandbox. A failed copy is removed again, so the caller can restore what it set aside. When
     * the source can't be removed afterwards, a single file's copy is removed too (nothing moved);
     * a directory may already be partly removed, so its copy is kept and [CopyOutcome.SOURCE_LEFT]
     * reports that both exist.
     */
    private fun copyThenDelete(source: File, destination: File): CopyOutcome {
        if (PathValidator.isSymlink(source) || PathValidator.containsSymlink(source)) return CopyOutcome.FAILED
        val copied = try {
            source.copyRecursively(destination, overwrite = false)
        } catch (_: Exception) {
            false
        }
        if (!copied) {
            destination.deleteRecursively()
            return CopyOutcome.FAILED
        }
        if (source.deleteRecursively()) return CopyOutcome.MOVED
        if (source.isFile) {
            destination.delete()
            return CopyOutcome.FAILED
        }
        return CopyOutcome.SOURCE_LEFT
    }
}
