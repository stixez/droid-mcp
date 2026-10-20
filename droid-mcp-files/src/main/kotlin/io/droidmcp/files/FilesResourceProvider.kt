package io.droidmcp.files

import android.os.Environment
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLConnection
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * Serves files under external storage as MCP resources at `file:///storage/...` URIs, inside the
 * same [PathValidator] sandbox as the file tools. A text file (by extension or content) of at most
 * [MAX_TEXT_BYTES] that is valid UTF-8 comes back as text, byte-for-byte (line endings untouched);
 * anything else as a base64 blob up to [MAX_BYTES], so nothing is silently truncated or altered.
 * Nothing is listed up front — clients find paths with `browse_files` / `search_files`.
 * Hidden while `read_file` is gated off.
 */
class FilesResourceProvider : ResourceProvider {

    override val requiredTools = setOf("read_file")

    override val templates: List<McpResourceTemplate>
        get() = listOf(
            McpResourceTemplate(
                "file://${Environment.getExternalStorageDirectory().absolutePath}/{+path}",
                name = "file",
                title = "File on shared storage",
                description = "A file under external storage; path is relative to the storage root (see browse_files).",
            ),
        )

    override fun handles(uri: String): Boolean = uri.startsWith("file:", ignoreCase = true)

    override suspend fun read(uri: String): List<ResourceContents>? {
        val file = fileOf(uri) ?: return null
        if (PathValidator.validate(file.path) != null) return null
        if (!file.isFile || !file.canRead()) return null
        val length = file.length()
        if (length > MAX_BYTES) return null
        val mimeType = URLConnection.guessContentTypeFromName(file.name)
        return try {
            val bytes = file.readBytes()
            val text = if (bytes.size <= MAX_TEXT_BYTES && isText(bytes, mimeType)) decodeUtf8(bytes) else null
            if (text != null) {
                listOf(ResourceContents.Text(uri, text, mimeType ?: "text/plain"))
            } else {
                listOf(ResourceContents.Blob(uri, Base64.getEncoder().encodeToString(bytes), mimeType ?: "application/octet-stream"))
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Text by MIME type, by a known text type, or by having no NUL bytes in its first 8 KB. */
    private fun isText(bytes: ByteArray, mimeType: String?): Boolean {
        if (mimeType != null) {
            if (mimeType.startsWith("text/") || mimeType in TEXT_MIME_TYPES) return true
            if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) return false
        }
        return (0 until minOf(bytes.size, 8192)).none { bytes[it] == 0.toByte() }
    }

    /** [bytes] as UTF-8, or null when they aren't valid UTF-8 (then the file is served as a blob). */
    private fun decodeUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /**
     * The file a `file:` URI names. Parsed by hand rather than with `java.net.URI`: clients often send raw
     * paths, where `#`, `?` and spaces are part of the file name, not a fragment, query or error.
     * Only `%XX` escapes are decoded (so `%23` is a `#`); `+` stays a plus.
     */
    private fun fileOf(uri: String): File? {
        if (!uri.startsWith("file:", ignoreCase = true)) return null
        var rest = uri.substring(5)
        if (rest.startsWith("//")) {
            rest = rest.substring(2)
            // file://host/path: only an empty host or localhost refers to this device.
            val slash = rest.indexOf('/')
            if (slash < 0) return null
            val host = rest.substring(0, slash)
            if (host.isNotEmpty() && !host.equals("localhost", ignoreCase = true)) return null
            rest = rest.substring(slash)
        }
        return percentDecode(rest)?.takeIf { it.startsWith("/") }?.let(::File)
    }

    private fun percentDecode(s: String): String? {
        if ('%' !in s) return s
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%') {
                if (i + 2 >= s.length) return null
                out.write(s.substring(i + 1, i + 3).toIntOrNull(16) ?: return null)
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/** Largest file `resources/read` returns, as text or blob. */
private const val MAX_BYTES = 10L * 1024 * 1024

/** Largest file returned as text; bigger text files come back as a blob instead of truncated. */
private const val MAX_TEXT_BYTES = 1024 * 1024

private val TEXT_MIME_TYPES = setOf("application/json", "application/xml", "application/javascript")
