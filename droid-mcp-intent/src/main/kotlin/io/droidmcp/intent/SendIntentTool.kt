package io.droidmcp.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Fires an Android activity intent restricted to an allowlist of safe actions (VIEW, DIAL,
 * SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH, EDIT) — non-allowlisted actions are rejected.
 * Picker actions (PICK, GET_CONTENT, OPEN_DOCUMENT, CREATE_DOCUMENT) are deliberately not
 * allowed: they only make sense with `startActivityForResult`, and this tool never reads a
 * result. Supports `data` URI, `type`, `package_name`, and `extras` (coerced to string
 * extras), always with `FLAG_ACTIVITY_NEW_TASK`. No permissions.
 *
 * A `data` URI must use a scheme on [IntentGuards.ALLOWED_SCHEMES], else
 * `uri_scheme_not_allowed`. On API 29+ the host must be visible or hold
 * `SYSTEM_ALERT_WINDOW`, else `background_activity_launch_blocked`.
 *
 * Output: `success`, `action`, `data`, `package`.
 */
class SendIntentTool(private val context: Context) : McpTool {

    override val name = "send_intent"
    override val description = "Fire a safe Android intent. Supports configurable action, data URI, MIME type, extras, and optional target package. Only allowlisted actions are permitted (VIEW, DIAL, SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH, EDIT). Data URIs must use http, https, geo, tel, mailto, sms, smsto, mms, mmsto, or market. Requires the host app to be in the foreground (or hold 'Display over other apps') on Android 10+."
    override val parameters = listOf(
        ToolParameter("action", "Intent action (e.g. 'android.intent.action.VIEW', 'android.intent.action.DIAL')", ParameterType.STRING, required = true),
        ToolParameter("data", "Data URI (e.g. 'tel:+1234567890', 'https://example.com')", ParameterType.STRING),
        ToolParameter("type", "MIME type (e.g. 'text/plain', 'image/*')", ParameterType.STRING),
        ToolParameter("package_name", "Target package for explicit intent (e.g. 'com.google.android.apps.maps')", ParameterType.STRING),
        ToolParameter("extras", "Key-value pairs to add as string extras", ParameterType.OBJECT),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    private val allowedActions = setOf(
        Intent.ACTION_VIEW,
        Intent.ACTION_DIAL,
        Intent.ACTION_SEND,
        Intent.ACTION_SENDTO,
        Intent.ACTION_CHOOSER,
        Intent.ACTION_SEARCH,
        Intent.ACTION_WEB_SEARCH,
        Intent.ACTION_EDIT,
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val action = params["action"]?.toString()
            ?: return ToolResult.error("action is required")

        if (action !in allowedActions) {
            return ToolResult.error("Action not allowed: $action. Allowed: VIEW, DIAL, SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH, EDIT")
        }

        val dataUri = params["data"]?.toString()?.let { raw ->
            IntentGuards.parseAllowedUri(raw).getOrElse {
                return ToolResult.error(it.message ?: "uri_scheme_not_allowed")
            }
        }
        IntentGuards.backgroundLaunchError(context)?.let { return it }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // setData() clears any previously-set type and setType() clears any
            // previously-set data — setting both separately silently drops whichever
            // was set first. setDataAndType() sets both atomically.
            val mimeType = params["type"]?.toString()
            when {
                dataUri != null && mimeType != null -> setDataAndType(dataUri, mimeType)
                dataUri != null -> data = dataUri
                mimeType != null -> type = mimeType
            }
            params["package_name"]?.toString()?.let { setPackage(it) }

            @Suppress("UNCHECKED_CAST")
            (params["extras"] as? Map<String, Any>)?.forEach { (key, value) ->
                putExtra(key, value.toString())
            }
        }

        return try {
            context.startActivity(intent)
            ToolResult.success(mapOf(
                "success" to true,
                "action" to action,
                "data" to params["data"]?.toString(),
                "package" to params["package_name"]?.toString(),
            ))
        } catch (e: ActivityNotFoundException) {
            ToolResult.error("No app found to handle this intent: $action")
        } catch (e: Exception) {
            ToolResult.error("Failed to send intent: ${e.message}")
        }
    }
}
