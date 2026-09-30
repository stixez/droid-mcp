package io.droidmcp.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.droidmcp.core.*
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.*

/**
 * Searches the MediaStore for images and/or videos by `DISPLAY_NAME` substring (`%`, `_`, `\`
 * match literally) and/or `DATE_TAKEN` range (strict `yyyy-MM-dd`, device timezone),
 * newest-first, with `limit`/`offset` paging. The `media_type` param selects `images`,
 * `videos`, or `all` (default); `all` runs one `MediaStore.Files` query filtered to image and
 * video rows, so both kinds are merged by date and `offset` pages consistently across them.
 * Requires `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO` on API 33+, else `READ_EXTERNAL_STORAGE`.
 * Output: `results` (each `{id, name, path, date_taken, size_bytes, mime_type, width,
 * height, media_type}`), `count`, and `media_type`.
 */
class SearchMediaTool(private val context: Context) : McpTool {

    override val name = "search_media"
    override val description = "Search photos and videos by keyword in display name, or by date range. Returns file name, path, date taken, size, mime type, and dimensions."
    override val parameters = listOf(
        ToolParameter("query", "Filename keyword to search for (case-insensitive substring). Optional.", ParameterType.STRING),
        ToolParameter("start_date", "Filter by date taken from (YYYY-MM-DD). Optional.", ParameterType.STRING),
        ToolParameter("end_date", "Filter by date taken until (YYYY-MM-DD, inclusive). Optional.", ParameterType.STRING),
        ToolParameter("media_type", "Type of media to search: 'images', 'videos', or 'all'. Default: 'all'", ParameterType.STRING, enumValues = listOf("images", "videos", "all")),
        ToolParameter("limit", "Max number of results to return. Default 10.", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0),
        ToolParameter("offset", "Number of results to skip for pagination. Default 0.", ParameterType.INTEGER, minimum = 0.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
        val startDateStr = params["start_date"]?.toString()
        val endDateStr = params["end_date"]?.toString()
        val mediaType = params["media_type"]?.toString()?.lowercase() ?: "all"
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10
        val offset = (params["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val startMillis = startDateStr?.let {
            (parseDate(it) ?: return ToolResult.error("Invalid start_date format. Use YYYY-MM-DD")).time
        }
        val endMillis = endDateStr?.let {
            val parsed = parseDate(it) ?: return ToolResult.error("Invalid end_date format. Use YYYY-MM-DD")
            // End of day; Calendar.add rather than +86_400_000 so a DST switch doesn't shift it.
            Calendar.getInstance().apply {
                time = parsed
                add(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis
        }

        // A single cursor per call: for 'all', MediaStore.Files filtered to image+video rows,
        // so results interleave by date and `offset` pages through one consistent ordering
        // (previously images and videos were queried back to back and concatenated).
        val uri: Uri
        val typeCondition: String?
        when (mediaType) {
            "images" -> { uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI; typeCondition = null }
            "videos" -> { uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI; typeCondition = null }
            "all" -> {
                uri = MediaStore.Files.getContentUri("external")
                typeCondition = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            }
            else -> return ToolResult.error("Invalid media_type '$mediaType'. Use: images, videos, all")
        }

        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
        )
        if (mediaType == "all") projection.add(MediaStore.Files.FileColumns.MEDIA_TYPE)

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        typeCondition?.let { conditions.add(it) }
        query?.let {
            conditions.add("${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'")
            args.add("%${escapeLike(it)}%")
        }
        startMillis?.let {
            conditions.add("${MediaStore.MediaColumns.DATE_TAKEN} >= ?")
            args.add(it.toString())
        }
        endMillis?.let {
            conditions.add("${MediaStore.MediaColumns.DATE_TAKEN} <= ?")
            args.add(it.toString())
        }

        val selection = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")
        val selectionArgs = if (args.isEmpty()) null else args.toTypedArray()
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

        val results = mutableListOf<Map<String, Any?>>()
        context.contentResolver.query(uri, projection.toTypedArray(), selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val typeIdx = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                var skipped = 0
                while (cursor.moveToNext() && results.size < limit) {
                    // Paging in the cursor loop — LIMIT/OFFSET in sortOrder isn't portable.
                    if (skipped < offset) {
                        skipped++
                        continue
                    }
                    val dateTaken = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                    val isVideo = when (mediaType) {
                        "videos" -> true
                        "images" -> false
                        else -> typeIdx >= 0 &&
                            cursor.getInt(typeIdx) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    }
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

        return ToolResult.success(mapOf(
            "results" to results,
            "count" to results.size,
            "media_type" to mediaType,
        ))
    }

    /** Strict `yyyy-MM-dd` parse (device timezone): non-lenient and the whole string must match. */
    internal fun parseDate(input: String): Date? {
        val text = input.trim()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val pos = ParsePosition(0)
        val date = format.parse(text, pos) ?: return null
        return if (pos.errorIndex < 0 && pos.index == text.length) date else null
    }

    /** Escapes `\`, `%`, `_` so [value] matches literally inside a `LIKE ? ESCAPE '\'` clause. */
    internal fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
