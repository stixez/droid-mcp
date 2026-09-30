package io.droidmcp.web

import okhttp3.Response

/** Upper bound on how much of a response body is ever buffered into memory at once. */
private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024 // 5 MB

/** A response body read by [readBounded]: the decoded [text] and whether the byte cap cut it short. */
internal class BoundedBody(val text: String, val truncated: Boolean)

/**
 * Reads [response]'s body as text, capped at [MAX_RESPONSE_BYTES] regardless of what the
 * server claims via `Content-Length` (chunked responses have none) — `ResponseBody.string()`
 * has no such cap and would buffer an arbitrarily large body (a multi-GB file at a URL passed
 * to `fetch_webpage`, or a hostile response to `web_search`) fully into memory before this
 * module's own `max_length`/`limit` truncation ever runs.
 *
 * Decodes with the charset from the response's `Content-Type` header, falling back to UTF-8.
 * [BoundedBody.truncated] is true when the body continued past the cap.
 *
 * Returns `null` if there is no body at all (matches the previous `body?.string()` contract).
 */
internal fun readBounded(response: Response): BoundedBody? {
    val body = response.body ?: return null
    val charset = try {
        body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    } catch (_: Exception) {
        Charsets.UTF_8
    }
    return body.source().use { source ->
        val buffer = okio.Buffer()
        var total = 0L
        var truncated = false
        while (true) {
            if (total >= MAX_RESPONSE_BYTES) {
                // Anything left beyond the cap means we cut the body short.
                truncated = source.request(1)
                break
            }
            val read = source.read(buffer, minOf(8192L, MAX_RESPONSE_BYTES - total))
            if (read == -1L) break
            total += read
        }
        BoundedBody(buffer.readString(charset), truncated)
    }
}
