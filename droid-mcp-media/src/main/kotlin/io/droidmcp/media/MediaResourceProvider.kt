package io.droidmcp.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.droidmcp.core.McpResource
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64

/**
 * Serves photos and videos as MCP resources: `resources/list` advertises the [RECENT_LIMIT] most
 * recent images and videos, and `resources/read` on a `content://media/external/{images,video}/media/<id>`
 * URI returns the file as a base64 blob. Items over [MAX_BYTES] can't be read (use
 * `get_media_metadata` for those). Needs the same permissions as [MediaTools]; without them the
 * listing is empty and reads fail as not found. Hidden while `get_media_metadata` is gated off.
 */
class MediaResourceProvider(private val context: Context) : ResourceProvider {

    override val requiredTools = setOf("get_media_metadata")

    override val templates = listOf(
        McpResourceTemplate(
            "${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}/{id}",
            name = "image",
            title = "Photo by MediaStore id",
            description = "An image from the device gallery; id is the `id` from search_media.",
        ),
        McpResourceTemplate(
            "${MediaStore.Video.Media.EXTERNAL_CONTENT_URI}/{id}",
            name = "video",
            title = "Video by MediaStore id",
            description = "A video from the device gallery; id is the `id` from search_media.",
        ),
    )

    override suspend fun list(): List<McpResource> {
        if (!MediaTools.hasPermissions(context)) return emptyList()
        return (recent(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image") +
            recent(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video"))
            .sortedByDescending { it.first }
            .take(RECENT_LIMIT)
            .map { it.second }
    }

    override fun handles(uri: String): Boolean = parse(uri) != null

    override suspend fun read(uri: String): List<ResourceContents>? {
        val itemUri = parse(uri) ?: return null
        if (!MediaTools.hasPermissions(context)) return null
        val resolver = context.contentResolver
        val (size, mimeType) = try {
            resolver.query(
                itemUri,
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE),
                null, null, null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                cursor.getLong(0) to cursor.getString(1)
            }
        } catch (_: SecurityException) {
            null // permission revoked between the check and the query
        } ?: return null
        if (size > MAX_BYTES) return null
        // SIZE can be 0 or stale, so never read more than the cap regardless of what it says.
        val bytes = try {
            resolver.openInputStream(itemUri)?.use { readAtMost(it, MAX_BYTES.toInt()) }
        } catch (_: Exception) {
            null
        } ?: return null
        return listOf(ResourceContents.Blob(uri, Base64.getEncoder().encodeToString(bytes), mimeType))
    }

    /** Recent items of one collection as (date added, resource) pairs. */
    private fun recent(collection: Uri, kind: String): List<Pair<Long, McpResource>> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val out = mutableListOf<Pair<Long, McpResource>>()
        try {
            context.contentResolver.query(
                collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val addedIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext() && out.size < RECENT_LIMIT) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: "$kind-$id"
                    out += cursor.getLong(addedIdx) to McpResource(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        name = name,
                        title = name,
                        description = "Recent $kind",
                        mimeType = cursor.getString(mimeIdx),
                        size = cursor.getLong(sizeIdx).takeIf { it > 0 },
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the query.
        }
        return out
    }

    /** The MediaStore item URI for a supported resource URI, or null. */
    private fun parse(uri: String): Uri? {
        for (collection in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) {
            val prefix = "$collection/"
            if (!uri.startsWith(prefix)) continue
            val id = uri.removePrefix(prefix).toLongOrNull()?.takeIf { it > 0 } ?: return null
            return ContentUris.withAppendedId(collection, id)
        }
        return null
    }

    /** The stream's bytes, or null when it holds more than [limit] (only `limit + 1` are ever buffered). */
    private fun readAtMost(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }
}

/** Items advertised by `resources/list`. */
private const val RECENT_LIMIT = 50

/** Largest item `resources/read` will return (base64 grows it by a third). */
private const val MAX_BYTES = 10L * 1024 * 1024
