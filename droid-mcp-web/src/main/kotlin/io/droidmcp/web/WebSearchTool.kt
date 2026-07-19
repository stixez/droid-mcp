package io.droidmcp.web

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/**
 * Searches the web by scraping DuckDuckGo's HTML endpoint (`html.duckduckgo.com`) with OkHttp and
 * parsing results with Jsoup. Reaches the network (`openWorldHint`); requires `INTERNET`
 * (always-granted). Results depend on DuckDuckGo's HTML structure (`.result` / `.result__a` /
 * `.result__snippet`) — a markup change can yield zero parsed results without an error. Any HTTP or
 * parse failure is caught and returned as a [ToolResult.error]; a non-2xx status returns an error too.
 *
 * Output map: `query` (echoed), `results` (`List<Map>` each with `title`/`url`/`snippet`),
 * `result_count` (Int).
 */
class WebSearchTool : McpTool {

    override val name = "web_search"
    override val description = "Search the web using DuckDuckGo and return titles, URLs, and snippets"
    override val parameters = listOf(
        ToolParameter("query", "Search query", ParameterType.STRING, required = true),
        ToolParameter("limit", "Maximum number of results to return (default: 5)", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = true)

    private val client = OkHttpClient()

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val query = params["query"]?.toString()
            ?: return@withContext ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 5

        val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = readBounded(response)
                ?: return@withContext ToolResult.error("Empty response from DuckDuckGo")

            if (!response.isSuccessful) {
                return@withContext ToolResult.error("DuckDuckGo returned HTTP ${response.code}")
            }

            val doc = Jsoup.parse(body)
            val resultElements = doc.select(".result").take(limit)

            val results = resultElements.mapNotNull { el ->
                val titleEl = el.selectFirst(".result__a") ?: return@mapNotNull null
                val snippetEl = el.selectFirst(".result__snippet")

                val title = titleEl.text().trim()
                val href = titleEl.attr("href").trim()
                val snippet = snippetEl?.text()?.trim() ?: ""

                if (title.isEmpty() || href.isEmpty()) return@mapNotNull null

                mapOf(
                    "title" to title,
                    "url" to resolveResultUrl(href),
                    "snippet" to snippet,
                )
            }

            ToolResult.success(mapOf(
                "query" to query,
                "results" to results,
                "result_count" to results.size,
            ))
        } catch (e: Exception) {
            ToolResult.error("Web search failed: ${e.message}")
        }
    }

    /**
     * html.duckduckgo.com result anchors are protocol-relative redirector links
     * (`//duckduckgo.com/l/?uddg=<url-encoded-destination>&rut=...`), not the destination URL
     * itself. Decode `uddg` when present; fall back to the raw href for any other shape.
     */
    private fun resolveResultUrl(href: String): String {
        val encoded = Regex("""uddg=([^&]+)""").find(href)?.groupValues?.get(1) ?: return href
        return try {
            java.net.URLDecoder.decode(encoded, "UTF-8")
        } catch (e: Exception) {
            href
        }
    }
}
