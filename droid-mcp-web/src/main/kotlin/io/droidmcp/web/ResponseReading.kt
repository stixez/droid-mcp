package io.droidmcp.web

import okhttp3.Response

/** Upper bound on how much of a response body is ever buffered into memory at once. */
private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024 // 5 MB

/**
 * Reads [response]'s body as UTF-8 text, capped at [MAX_RESPONSE_BYTES] regardless of what the
 * server claims via `Content-Length` (chunked responses have none) — `ResponseBody.string()`
 * has no such cap and would buffer an arbitrarily large body (a multi-GB file at a URL passed
 * to `fetch_webpage`, or a hostile response to `web_search`) fully into memory before this
 * module's own `max_length`/`limit` truncation ever runs.
 *
 * Returns `null` if there is no body at all (matches the previous `body?.string()` contract).
 */
internal fun readBounded(response: Response): String? {
    val body = response.body ?: return null
    return body.source().use { source ->
        val buffer = okio.Buffer()
        var total = 0L
        while (total < MAX_RESPONSE_BYTES) {
            val read = source.read(buffer, minOf(8192L, MAX_RESPONSE_BYTES - total))
            if (read == -1L) break
            total += read
        }
        buffer.readString(Charsets.UTF_8)
    }
}
