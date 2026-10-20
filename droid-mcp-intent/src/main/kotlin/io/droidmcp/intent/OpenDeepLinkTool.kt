package io.droidmcp.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Opens a `uri` via ACTION_VIEW, optionally forced to a specific app via `package_name`, with
 * `FLAG_ACTIVITY_NEW_TASK`. No permissions.
 *
 * The URI scheme must be on [IntentGuards.ALLOWED_SCHEMES] (http, https, geo, tel, mailto,
 * sms, smsto, mms, mmsto, market); `file`, `content`, `intent`, `android-app`, `javascript`,
 * app-private and unknown schemes are rejected with `uri_scheme_not_allowed`. On API 29+ the
 * host must be visible or hold `SYSTEM_ALERT_WINDOW`, else `background_activity_launch_blocked`
 * (see [IntentGuards.backgroundLaunchError]) — the system would otherwise drop the launch
 * silently.
 *
 * Output: `success`, `uri`, `package`.
 */
class OpenDeepLinkTool(private val context: Context) : McpTool {

    override val name = "open_deep_link"
    override val description = "Open a link in the app that handles it: http(s) URLs, maps (geo:), phone (tel:), email (mailto:), messages (sms:, mms:) or Play Store (market:). Pass package_name to choose the app. Needs the host app in the foreground on Android 10+."
    override val parameters = listOf(
        ToolParameter("uri", "The URI to open (e.g. 'https://maps.google.com/?q=...', 'geo:37.7749,-122.4194')", ParameterType.STRING, required = true),
        ToolParameter("package_name", "Optional: force open in a specific app", ParameterType.STRING),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        boolean("success")
        string("uri")
        string("package", nullable = true)
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val uri = params["uri"]?.toString()
            ?: return ToolResult.error("uri is required")

        val parsed = IntentGuards.parseAllowedUri(uri).getOrElse {
            return ToolResult.error(it.message ?: "uri_scheme_not_allowed")
        }
        IntentGuards.backgroundLaunchError(context)?.let { return it }

        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
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
