package io.droidmcp.web

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Fetches a URL with OkHttp (15s connect/read timeouts, 30s overall call timeout) and extracts
 * readable body text via Jsoup, stripping script/style/noscript/nav/footer/header before
 * extraction. Reaches the network (`openWorldHint`); requires `INTERNET` (always-granted). Only
 * `http`/`https` URLs are accepted; a malformed URL returns an "Invalid URL" error; non-2xx status,
 * empty body, or any other failure returns a [ToolResult.error]. The response body is capped at
 * 5 MB while reading and decoded with the `Content-Type` charset (see [readBounded]) regardless of
 * `max_length`.
 *
 * SSRF protection: by default the tool refuses loopback, private (RFC 1918 / fc00::/7),
 * link-local (incl. the 169.254.169.254 metadata address), CGNAT, unspecified and multicast
 * targets — checked at DNS resolution and again on every redirect hop (see [NetworkGuard]), so a
 * public URL can't redirect into the LAN either. Pass `allowPrivateNetwork = true` (or use
 * `WebTools.all(context, allowPrivateNetwork = true)`) to let the token holder reach the phone's
 * local network deliberately.
 *
 * Output map: `title` (String), `url` (echoed), `content` (text truncated to `max_length`),
 * `content_length` (Int — full untruncated length of the extracted text), `response_truncated`
 * (Boolean — the raw body exceeded the 5 MB read cap).
 *
 * @param allowPrivateNetwork when true, disables the SSRF guard (default false).
 */
class FetchWebpageTool @JvmOverloads constructor(
    private val allowPrivateNetwork: Boolean = false,
) : McpTool {

    override val name = "fetch_webpage"
    override val description = "Fetch an http(s) URL and extract readable text content from the page. Private/local network addresses are blocked by default."
    override val parameters = listOf(
        ToolParameter("url", "URL to fetch", ParameterType.STRING, required = true),
        ToolParameter("max_length", "Maximum characters to return (default: 2000)", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true)

    private val client = NetworkGuard.newClient(allowPrivateNetwork)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val url = params["url"]?.toString()
            ?: return@withContext ToolResult.error("url is required")
        val maxLength = (params["max_length"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 2000

        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext if (url.contains("://") && !url.startsWith("http://", ignoreCase = true) &&
                !url.startsWith("https://", ignoreCase = true)
            ) {
                ToolResult.error("Invalid URL: $url — only http and https URLs are supported")
            } else {
                ToolResult.error("Invalid URL: $url")
            }

        val request = Request.Builder()
            .url(httpUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .build()

        try {
            val (bounded, response) = client.newCall(request).execute().use { response ->
                readBounded(response) to response
            }
            bounded ?: return@withContext ToolResult.error("Empty response from $url")
            val body = bounded.text

            if (!response.isSuccessful) {
                return@withContext ToolResult.error("Server returned HTTP ${response.code} for $url")
            }

            val doc = Jsoup.parse(body, url)
            val title = doc.title().trim()

            // Remove scripts and styles before extracting text
            doc.select("script, style, noscript, nav, footer, header").remove()

            val bodyEl = doc.body()
            val fullText = bodyEl.text().trim()
            val truncated = if (fullText.length > maxLength) fullText.substring(0, maxLength) else fullText

            ToolResult.success(mapOf(
                "title" to title,
                "url" to url,
                "content" to truncated,
                "content_length" to fullText.length,
                "response_truncated" to bounded.truncated,
            ))
        } catch (e: IllegalArgumentException) {
            ToolResult.error("Invalid URL: $url")
        } catch (e: Exception) {
            ToolResult.error("Failed to fetch webpage: ${e.message}")
        }
    }
}
