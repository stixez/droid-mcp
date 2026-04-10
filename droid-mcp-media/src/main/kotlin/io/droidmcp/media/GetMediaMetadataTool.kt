package io.droidmcp.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class GetMediaMetadataTool(private val context: Context) : McpTool {

    override val name = "get_media_metadata"
    override val description = "Get detailed metadata for a specific media file by its MediaStore ID. Returns full details including date, dimensions, size, location (if available), and video duration."
    override val parameters = listOf(
        ToolParameter("media_id", "MediaStore media ID (from search_media results)", ParameterType.INTEGER, required = true),
        ToolParameter("media_type", "Type of media: 'image' or 'video'. Default: 'image'", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val mediaId = (params["media_id"] as? Number)?.toLong()
            ?: return ToolResult.error("media_id is required and must be a number")
        val mediaType = params["media_type"]?.toString()?.lowercase() ?: "image"

        val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val (contentUri, isVideo) = when (mediaType) {
            "image" -> Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
            "video" -> Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            else -> return ToolResult.error("Invalid media_type '$mediaType'. Use: image, video")
        }

        val itemUri = Uri.withAppendedPath(contentUri, mediaId.toString())

        val imageProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.ImageColumns.LATITUDE,
            MediaStore.Images.ImageColumns.LONGITUDE,
            MediaStore.Images.ImageColumns.DESCRIPTION,
        )

        val videoProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.VideoColumns.RESOLUTION,
        )

        val projection = if (isVideo) videoProjection else imageProjection

        var metadata: Map<String, Any?>? = null

        context.contentResolver.query(itemUri, projection, null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                    val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                    val base = mutableMapOf<String, Any?>(
                        "id" to cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
                        "name" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)),
                        "path" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)),
                        "date_taken" to if (dateTaken > 0) displayFormat.format(Date(dateTaken)) else null,
                        "date_modified" to if (dateModified > 0) displayFormat.format(Date(dateModified * 1000)) else null,
                        "size_bytes" to cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                        "mime_type" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)),
                        "width" to cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)),
                        "height" to cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)),
                        "media_type" to if (isVideo) "video" else "image",
                    )
                    if (isVideo) {
                        val durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION))
                        base["duration_seconds"] = if (durationMs > 0) durationMs / 1000.0 else null
                        base["resolution"] = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.RESOLUTION))
                    } else {
                        val latIdx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.LATITUDE)
                        val lngIdx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.LONGITUDE)
                        if (latIdx >= 0 && lngIdx >= 0) {
                            val lat = cursor.getDouble(latIdx)
                            val lng = cursor.getDouble(lngIdx)
                            base["latitude"] = if (lat != 0.0) lat else null
                            base["longitude"] = if (lng != 0.0) lng else null
                        }
                        val descIdx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DESCRIPTION)
                        if (descIdx >= 0) base["description"] = cursor.getString(descIdx)
                    }
                    metadata = base
                }
            }

        return if (metadata != null) {
            ToolResult.success(metadata!!)
        } else {
            ToolResult.error("No media found with id $mediaId (type=$mediaType)")
        }
    }
}
