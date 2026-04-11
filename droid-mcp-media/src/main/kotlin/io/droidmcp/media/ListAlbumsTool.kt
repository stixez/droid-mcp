package io.droidmcp.media

import android.content.Context
import android.provider.MediaStore
import io.droidmcp.core.*

class ListAlbumsTool(private val context: Context) : McpTool {

    override val name = "list_albums"
    override val description = "List photo albums (MediaStore bucket/folders) on the device. Returns album name, item count, and cover image ID."
    override val parameters = listOf(
        ToolParameter("limit", "Max number of albums to return. Default 10.", ParameterType.INTEGER),
        ToolParameter("media_type", "Type of media: 'images', 'videos', or 'all'. Default: 'images'", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10
        val mediaType = params["media_type"]?.toString()?.lowercase() ?: "images"

        val albums = mutableMapOf<String, MutableMap<String, Any?>>()

        fun queryAlbums(uri: android.net.Uri) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            )
            context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID))
                        ?: continue
                    if (bucketId !in albums) {
                        albums[bucketId] = mutableMapOf(
                            "bucket_id" to bucketId,
                            "album_name" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)),
                            "cover_media_id" to cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
                            "count" to 0,
                        )
                    }
                    @Suppress("UNCHECKED_CAST")
                    val album = albums[bucketId]!!
                    album["count"] = (album["count"] as Int) + 1
                }
            }
        }

        when (mediaType) {
            "images" -> queryAlbums(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            "videos" -> queryAlbums(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            "all" -> {
                queryAlbums(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                queryAlbums(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            }
            else -> return ToolResult.error("Invalid media_type '$mediaType'. Use: images, videos, all")
        }

        val albumList = albums.values
            .sortedByDescending { it["count"] as Int }
            .take(limit)

        return ToolResult.success(mapOf(
            "albums" to albumList,
            "count" to albumList.size,
            "media_type" to mediaType,
        ))
    }
}
