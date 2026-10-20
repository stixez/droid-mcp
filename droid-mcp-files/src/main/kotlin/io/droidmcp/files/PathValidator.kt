package io.droidmcp.files

import android.os.Environment
import java.io.File
import java.nio.file.Files

/**
 * Sandbox guard for the file tools. Resolves paths to their canonical form and
 * confirms they live under the device's external storage root, blocking traversal
 * (`..`) and symlink escapes out of the allowed area.
 */
internal object PathValidator {
    /** (absolute root, canonical root): re-resolved only when the reported root changes. */
    @Volatile
    private var cachedRoot: Pair<String, String>? = null

    // The root is read on every check so a remounted or test-substituted root is honoured, but its
    // canonical form (a filesystem lookup) is cached per absolute path.
    private val allowedRoots: List<String>
        get() {
            val dir = Environment.getExternalStorageDirectory().absolutePath
            val cached = cachedRoot
            val canonical = if (cached?.first == dir) cached.second else File(dir).canonicalPath.also { cachedRoot = dir to it }
            return listOf(canonical)
        }

    /**
     * @param path filesystem path to check
     * @return `true` if [path] canonically resolves to, or under, the external storage root
     */
    fun isAllowed(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return allowedRoots.any { root ->
            canonical == root || canonical.startsWith(root + File.separator)
        }
    }

    /**
     * @param path filesystem path to check
     * @return `null` if [path] is inside the sandbox, otherwise an error message suitable
     *   for returning straight to the caller
     */
    fun validate(path: String): String? = when {
        !isAllowed(path) -> "Access denied: path is outside allowed storage directories"
        hasDanglingLink(path) -> "Access denied: path goes through a broken symbolic link"
        else -> null
    }

    /**
     * Whether [path] or a not-yet-existing ancestor is a symbolic link whose target is missing.
     * `canonicalPath` can't resolve such a link, so it would pass [isAllowed] while a write through
     * it lands wherever the link points — possibly outside the sandbox.
     */
    fun hasDanglingLink(path: String): Boolean {
        var file: File? = File(path).absoluteFile
        while (file != null) {
            if (isSymlink(file) && !file.exists()) return true
            if (file.exists()) return false // existing components are resolved by canonicalPath
            file = file.parentFile
        }
        return false
    }

    /**
     * Whether the write tools must leave [path] alone: the storage root, a top-level folder (DCIM,
     * Download, …; plain files in the root are fine), or anything under `Android/`, which holds
     * other apps' external data and OBB files.
     */
    fun isProtected(path: String): Boolean {
        val file = File(path).canonicalFile
        val roots = allowedRoots
        if (file.path in roots) return true
        if (file.parentFile?.path in roots && file.isDirectory) return true
        return isInAndroidDir(path)
    }

    /** Whether [path] is the root's `Android/` folder or inside it. */
    fun isInAndroidDir(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return allowedRoots.any { root ->
            val android = root + File.separator + "Android"
            canonical == android || canonical.startsWith(android + File.separator)
        }
    }

    /** Whether [path] itself is a symbolic link (not following it). */
    fun isSymlink(path: File): Boolean = Files.isSymbolicLink(path.toPath())

    /**
     * Whether anything inside [dir] is a symbolic link. Recursive deletes and copies refuse such
     * trees: walking them would follow the link out of the sandbox.
     */
    fun containsSymlink(dir: File): Boolean =
        dir.walkTopDown().onEnter { it == dir || !isSymlink(it) }.any { it != dir && isSymlink(it) }
}
