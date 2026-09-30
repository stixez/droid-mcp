package io.droidmcp.files

import android.content.Context
import io.droidmcp.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*

/**
 * Recursively searches for files and directories whose name contains a case-insensitive
 * substring, sandboxed to external storage via [PathValidator] (defaults to `/sdcard`).
 * Recursion is capped at depth 5, 20 000 visited entries and 10 s per call; unreadable
 * directories are skipped silently, symlinks are neither followed nor reported, every result is
 * re-checked against [PathValidator], and the walk stops promptly when the call is cancelled.
 * Requires `READ_EXTERNAL_STORAGE` on API ≤32; uses File API access on API 33+. Under scoped
 * storage (API 30+) non-media files created by other apps are invisible to the walk.
 * Output: `query`, `search_path`, `results` (each `{name, path, size_bytes, last_modified,
 * is_directory}`) capped at the `limit` param, `count`, and `search_incomplete` (true when the
 * visit/time budget ran out before the tree was fully searched).
 */
class SearchFilesTool(private val context: Context) : McpTool {

    override val name = "search_files"
    override val description = "Search for files and directories by name pattern (case-insensitive substring match) recursively under a given directory (depth 5, bounded time budget). Returns matching paths with metadata. On Android 11+ non-media files created by other apps are not visible."
    override val parameters = listOf(
        ToolParameter("query", "Filename pattern to search for (case-insensitive substring)", ParameterType.STRING, required = true),
        ToolParameter("path", "Root directory to search in. Default: /sdcard", ParameterType.STRING),
        ToolParameter("limit", "Max number of results to return. Default 10.", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val path = params["path"]?.toString() ?: "/sdcard"
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        PathValidator.validate(path)?.let { return ToolResult.error(it) }

        val root = File(path)
        if (!root.exists()) return ToolResult.error("Path does not exist: $path")
        if (!root.isDirectory) return ToolResult.error("Path is not a directory: $path")
        if (!root.canRead()) return ToolResult.error("Cannot read directory: $path — check READ_EXTERNAL_STORAGE (API ≤32); on Android 11+ scoped storage also hides other apps' non-media files")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val results = mutableListOf<Map<String, Any?>>()
        val queryLower = query.lowercase()
        val deadline = System.currentTimeMillis() + TIME_BUDGET_MS
        var visited = 0
        var budgetExhausted = false

        // Bounded walk: depth ≤ 5, at most MAX_VISITED entries and TIME_BUDGET_MS of wall time
        // (a deep /sdcard/Android/media tree can hold hundreds of thousands of entries), and it
        // honours coroutine cancellation between directories.
        suspend fun searchDir(dir: File, currentDepth: Int, maxDepth: Int) {
            if (currentDepth > maxDepth) return
            if (results.size >= limit || budgetExhausted) return
            currentCoroutineContext().ensureActive()
            val children = try {
                dir.listFiles()
            } catch (_: SecurityException) {
                null // Skip directories we can't access
            } ?: return
            for (file in children) {
                if (results.size >= limit) return
                if (++visited > MAX_VISITED || System.currentTimeMillis() > deadline) {
                    budgetExhausted = true
                    return
                }
                if (visited % PROGRESS_EVERY == 0) {
                    reportProgress(visited.toDouble(), message = "Scanned $visited entries, ${results.size} matches")
                }
                // Never follow or report symlinks: they can loop, or point outside the sandbox.
                val isLink = try {
                    Files.isSymbolicLink(file.toPath())
                } catch (_: Exception) {
                    true
                }
                if (isLink) continue
                if (file.name.lowercase().contains(queryLower) && PathValidator.isAllowed(file.path)) {
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
        }

        searchDir(root, currentDepth = 0, maxDepth = 5)

        return ToolResult.success(mapOf(
            "query" to query,
            "search_path" to path,
            "results" to results,
            "count" to results.size,
            "search_incomplete" to budgetExhausted,
        ))
    }

    private companion object {
        /** Max directory entries examined per call. */
        const val MAX_VISITED = 20_000

        /** Report progress every this many visited entries. */
        private const val PROGRESS_EVERY = 500

        /** Max wall-clock time per call, in milliseconds. */
        const val TIME_BUDGET_MS = 10_000L
    }
}
