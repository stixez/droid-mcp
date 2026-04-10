package io.droidmcp.files

import android.os.Environment
import java.io.File

internal object PathValidator {
    private val allowedRoots by lazy {
        listOf(Environment.getExternalStorageDirectory().canonicalPath)
    }

    fun isAllowed(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return allowedRoots.any { canonical.startsWith(it) }
    }

    fun validate(path: String): String? {
        return if (isAllowed(path)) null
        else "Access denied: path is outside allowed storage directories"
    }
}
