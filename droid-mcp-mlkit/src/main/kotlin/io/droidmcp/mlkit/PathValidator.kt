package io.droidmcp.mlkit

import android.os.Environment
import java.io.File

/**
 * Sandboxes ML Kit image paths to the external-storage root, mirroring the file-tools convention.
 */
internal object PathValidator {
    private val externalRoot: String by lazy {
        Environment.getExternalStorageDirectory().canonicalPath
    }

    /**
     * @param path candidate absolute path.
     * @return true if [path] resolves to the external-storage root or a descendant of it.
     */
    fun isAllowed(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return canonical == externalRoot || canonical.startsWith(externalRoot + File.separator)
    }
}
