package io.droidmcp.web

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class FetchWebpageTool : McpTool {

    override val name = "fetch_webpage"
    override val description = "Fetch a URL and extract readable text content from the page"
    override val parameters = listOf(
        ToolParameter("url", "URL to fetch", ParameterType.STRING, required = true),
        ToolParameter("max_length", "Maximum characters to return (default: 2000)", ParameterType.INTEGER),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val url = params["url"]?.toString()
            ?: return ToolResult.error("url is required")
        val maxLength = (params["max_length"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 2000

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
                ?: return ToolResult.error("Empty response from $url")

            if (!response.isSuccessful) {
                return ToolResult.error("Server returned HTTP ${response.code} for $url")
            }

            val doc = Jsoup.parse(body, url)
            val title = doc.title().trim()

            // Remove scripts and styles before extracting text
            doc.select("script, style, noscript, nav, footer, header").remove()

            val bodyEl = doc.body()
            val fullText = bodyEl?.text()?.trim() ?: ""
            val truncated = if (fullText.length > maxLength) fullText.substring(0, maxLength) else fullText

            ToolResult.success(mapOf(
                "title" to title,
                "url" to url,
                "content" to truncated,
                "content_length" to fullText.length,
            ))
        } catch (e: IllegalArgumentException) {
            ToolResult.error("Invalid URL: $url")
        } catch (e: Exception) {
            ToolResult.error("Failed to fetch webpage: ${e.message}")
        }
    }
}
