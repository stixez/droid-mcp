package io.droidmcp.mlkit

import android.os.Environment
import java.io.File

internal object PathValidator {
    private val externalRoot: String by lazy {
        Environment.getExternalStorageDirectory().canonicalPath
    }

    fun isAllowed(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return canonical == externalRoot || canonical.startsWith(externalRoot + File.separator)
    }
}
