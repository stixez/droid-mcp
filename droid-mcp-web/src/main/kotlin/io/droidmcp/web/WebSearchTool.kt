package io.droidmcp.web

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class WebSearchTool : McpTool {

    override val name = "web_search"
    override val description = "Search the web using DuckDuckGo and return titles, URLs, and snippets"
    override val parameters = listOf(
        ToolParameter("query", "Search query", ParameterType.STRING, required = true),
        ToolParameter("limit", "Maximum number of results to return (default: 5)", ParameterType.INTEGER),
    )

    private val client = OkHttpClient()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 5

        val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
                ?: return ToolResult.error("Empty response from DuckDuckGo")

            if (!response.isSuccessful) {
                return ToolResult.error("DuckDuckGo returned HTTP ${response.code}")
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
                    "url" to href,
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
}
