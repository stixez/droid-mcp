package io.droidmcp.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import io.droidmcp.core.*

class SendMessageTool(private val context: Context) : McpTool {

    private val phoneRegex = Regex("^\\+?[0-9\\s\\-().]{7,20}$")

    override val name = "send_message"
    override val description = "Send an SMS message to a phone number"
    override val parameters = listOf(
        ToolParameter("to", "Recipient phone number", ParameterType.STRING, required = true),
        ToolParameter("body", "Message text", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val to = params["to"]?.toString()
            ?: return ToolResult.error("to is required")
        val body = params["body"]?.toString()
            ?: return ToolResult.error("body is required")

        if (!phoneRegex.matches(to)) {
            return ToolResult.error("Invalid phone number format: $to")
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            if (body.length > 160) {
                val parts = smsManager.divideMessage(body)
                smsManager.sendMultipartTextMessage(to, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(to, null, body, null, null)
            }
            ToolResult.success(mapOf(
                "sent" to true,
                "to" to to,
                "body_length" to body.length,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to send SMS: ${e.message}")
        }
    }
}
