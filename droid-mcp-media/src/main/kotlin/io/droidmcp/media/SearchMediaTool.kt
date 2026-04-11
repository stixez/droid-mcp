package io.droidmcp.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class SearchMediaTool(private val context: Context) : McpTool {

    override val name = "search_media"
    override val description = "Search photos and videos by keyword in display name, or by date range. Returns file name, path, date taken, size, mime type, and dimensions."
    override val parameters = listOf(
        ToolParameter("query", "Filename keyword to search for (case-insensitive substring). Optional.", ParameterType.STRING),
        ToolParameter("start_date", "Filter by date taken from (YYYY-MM-DD). Optional.", ParameterType.STRING),
        ToolParameter("end_date", "Filter by date taken until (YYYY-MM-DD, inclusive). Optional.", ParameterType.STRING),
        ToolParameter("media_type", "Type of media to search: 'images', 'videos', or 'all'. Default: 'all'", ParameterType.STRING),
        ToolParameter("limit", "Max number of results to return. Default 10.", ParameterType.INTEGER),
        ToolParameter("offset", "Number of results to skip for pagination. Default 0.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
        val startDateStr = params["start_date"]?.toString()
        val endDateStr = params["end_date"]?.toString()
        val mediaType = params["media_type"]?.toString()?.lowercase() ?: "all"
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10
        val offset = (params["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val startMillis = startDateStr?.let {
            try { dateFormat.parse(it)?.time?.div(1000) } catch (_: Exception) {
                return ToolResult.error("Invalid start_date format. Use YYYY-MM-DD")
            }
        }
        val endMillis = endDateStr?.let {
            try {
                val parsed = dateFormat.parse(it) ?: return ToolResult.error("Invalid end_date format")
                (parsed.time + 86_400_000L) / 1000 // end of day in seconds
            } catch (_: Exception) {
                return ToolResult.error("Invalid end_date format. Use YYYY-MM-DD")
            }
        }

        val results = mutableListOf<Map<String, Any?>>()

        fun queryUri(uri: Uri, isVideo: Boolean) {
            if (results.size >= limit + offset) return

            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
            )

            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            query?.let {
                conditions.add("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?")
                args.add("%$it%")
            }
            startMillis?.let {
                conditions.add("${MediaStore.MediaColumns.DATE_TAKEN} >= ?")
                args.add((it * 1000).toString())
            }
            endMillis?.let {
                conditions.add("${MediaStore.MediaColumns.DATE_TAKEN} <= ?")
                args.add((it * 1000).toString())
            }

            val selection = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")
            val selectionArgs = if (args.isEmpty()) null else args.toTypedArray()
            val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    while (cursor.moveToNext() && results.size < limit + offset) {
                        val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                        results.add(mapOf(
                            "id" to cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)),
                            "name" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)),
                            "path" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)),
                            "date_taken" to if (dateTaken > 0) displayFormat.format(Date(dateTaken)) else null,
                            "size_bytes" to cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                            "mime_type" to cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)),
                            "width" to cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)),
                            "height" to cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)),
                            "media_type" to if (isVideo) "video" else "image",
                        ))
                    }
                }
        }

        when (mediaType) {
            "images" -> queryUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
            "videos" -> queryUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            "all" -> {
                queryUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
                queryUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            }
            else -> return ToolResult.error("Invalid media_type '$mediaType'. Use: images, videos, all")
        }

        val paged = results.drop(offset).take(limit)

        return ToolResult.success(mapOf(
            "results" to paged,
            "count" to paged.size,
            "media_type" to mediaType,
        ))
    }
}
