package io.droidmcp.files

import android.os.Environment
import java.io.File

/**
 * Sandbox guard for the file tools. Resolves paths to their canonical form and
 * confirms they live under the device's external storage root, blocking traversal
 * (`..`) and symlink escapes out of the allowed area.
 */
internal object PathValidator {
    private val allowedRoots by lazy {
        listOf(Environment.getExternalStorageDirectory().canonicalPath)
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
    fun validate(path: String): String? {
        return if (isAllowed(path)) null
        else "Access denied: path is outside allowed storage directories"
    }
}
