package io.droidmcp.qr

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Validates the `image_uri` accepted by the scan tools, mirroring the file-tools / mlkit
 * `PathValidator` sandbox:
 *
 *  - `file://` URIs (and bare absolute paths, for convenience) must resolve — after
 *    canonicalization, so `..` and symlinks can't escape — to the external-storage root or a
 *    descendant, and point at an existing regular file.
 *  - `content://` URIs are allowed (MediaStore, document providers, etc.) except for authorities
 *    served by the host app itself, which could otherwise expose the host's private files (e.g. a
 *    `FileProvider` over its data dir).
 *  - Any other scheme is rejected.
 */
internal object ImageUriValidator {

    private val externalRoot: String by lazy {
        Environment.getExternalStorageDirectory().canonicalPath
    }

    /**
     * @param raw The `image_uri` param.
     * @return the [Uri] to hand to `InputImage.fromFilePath`, or a failure whose message is the
     *   tool error.
     */
    fun validate(context: Context, raw: String): Result<Uri> {
        val parsed = Uri.parse(raw.trim())
        return when (parsed.scheme?.lowercase()) {
            null -> if (raw.startsWith("/")) validateFile(File(raw.trim())) else deny("image_uri must be a file:// or content:// URI")
            ContentResolver.SCHEME_FILE -> {
                val path = parsed.path ?: return deny("image_uri has no path")
                validateFile(File(path))
            }
            ContentResolver.SCHEME_CONTENT -> {
                val authority = parsed.authority ?: return deny("image_uri has no authority")
                val owner = runCatching {
                    context.packageManager.resolveContentProvider(authority, 0)?.packageName
                }.getOrNull()
                if (owner == context.packageName) {
                    deny("Access denied: content URIs served by the host app are not allowed")
                } else {
                    Result.success(parsed)
                }
            }
            else -> deny("image_uri scheme '${parsed.scheme}' is not supported; use file:// or content://")
        }
    }

    private fun validateFile(file: File): Result<Uri> {
        val canonical = runCatching { file.canonicalPath }.getOrNull()
            ?: return deny("image_uri path could not be resolved")
        if (canonical != externalRoot && !canonical.startsWith(externalRoot + File.separator)) {
            return deny("Access denied: image_uri is outside allowed storage directories")
        }
        val resolved = File(canonical)
        if (!resolved.isFile) return deny("Image file not found: ${file.path}")
        return Result.success(Uri.fromFile(resolved))
    }

    private fun deny(message: String): Result<Uri> = Result.failure(IllegalArgumentException(message))
}
