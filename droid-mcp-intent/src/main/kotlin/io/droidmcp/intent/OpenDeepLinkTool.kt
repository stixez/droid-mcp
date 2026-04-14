package io.droidmcp.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class OpenDeepLinkTool(private val context: Context) : McpTool {

    override val name = "open_deep_link"
    override val description = "Open a deep link URI via ACTION_VIEW. Supports http/https URLs, app-specific schemes (e.g. 'spotify:track:...'), geo: URIs, tel: URIs, etc."
    override val parameters = listOf(
        ToolParameter("uri", "The URI to open (e.g. 'https://maps.google.com/?q=...', 'geo:37.7749,-122.4194')", ParameterType.STRING, required = true),
        ToolParameter("package_name", "Optional: force open in a specific app", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val uri = params["uri"]?.toString()
            ?: return ToolResult.error("uri is required")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            params["package_name"]?.toString()?.let { setPackage(it) }
        }

        return try {
            context.startActivity(intent)
            ToolResult.success(mapOf(
                "success" to true,
                "uri" to uri,
                "package" to params["package_name"]?.toString(),
            ))
        } catch (e: ActivityNotFoundException) {
            ToolResult.error("No app found to handle URI: $uri")
        } catch (e: Exception) {
            ToolResult.error("Failed to open deep link: ${e.message}")
        }
    }
}
