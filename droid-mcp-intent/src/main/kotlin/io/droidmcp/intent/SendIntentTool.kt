package io.droidmcp.intent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class SendIntentTool(private val context: Context) : McpTool {

    override val name = "send_intent"
    override val description = "Fire a safe Android intent. Supports configurable action, data URI, MIME type, extras, and optional target package. Only allowlisted actions are permitted (VIEW, DIAL, SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH, EDIT, PICK, GET_CONTENT, CREATE_DOCUMENT, OPEN_DOCUMENT)."
    override val parameters = listOf(
        ToolParameter("action", "Intent action (e.g. 'android.intent.action.VIEW', 'android.intent.action.DIAL')", ParameterType.STRING, required = true),
        ToolParameter("data", "Data URI (e.g. 'tel:+1234567890', 'https://example.com')", ParameterType.STRING),
        ToolParameter("type", "MIME type (e.g. 'text/plain', 'image/*')", ParameterType.STRING),
        ToolParameter("package_name", "Target package for explicit intent (e.g. 'com.google.android.apps.maps')", ParameterType.STRING),
        ToolParameter("extras", "Key-value pairs to add as string extras", ParameterType.OBJECT),
    )

    private val allowedActions = setOf(
        Intent.ACTION_VIEW,
        Intent.ACTION_DIAL,
        Intent.ACTION_SEND,
        Intent.ACTION_SENDTO,
        Intent.ACTION_CHOOSER,
        Intent.ACTION_SEARCH,
        Intent.ACTION_WEB_SEARCH,
        Intent.ACTION_EDIT,
        Intent.ACTION_PICK,
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_CREATE_DOCUMENT,
        Intent.ACTION_OPEN_DOCUMENT,
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val action = params["action"]?.toString()
            ?: return ToolResult.error("action is required")

        if (action !in allowedActions) {
            return ToolResult.error("Action not allowed: $action. Allowed: VIEW, DIAL, SEND, SENDTO, CHOOSER, SEARCH, WEB_SEARCH, EDIT, PICK, GET_CONTENT, CREATE_DOCUMENT, OPEN_DOCUMENT")
        }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            params["data"]?.toString()?.let { data = Uri.parse(it) }
            params["type"]?.toString()?.let { type = it }
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
