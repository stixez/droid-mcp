package io.droidmcp.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import io.droidmcp.core.*

/**
 * Sends an SMS to `to` with text `body` via `SmsManager` (version-checked: system service on
 * API 31+, else `getDefault()`). The recipient must match [phoneRegex] or an error is returned;
 * bodies over 160 chars are split with `divideMessage`/`sendMultipartTextMessage`. Requires
 * `SEND_SMS`. Output on success: `sent` (true), echoed `to`, and `body_length`; failures return
 * an error with the exception message.
 */
class SendMessageTool(private val context: Context) : McpTool {

    // Character-set check (+, digits, and common formatting punctuation) plus a separate
    // digit-count check (3-15, covering short codes through full E.164 numbers) — a
    // length-only regex like the previous one would both reject real short codes (3-6 digits)
    // and accept a pure-punctuation string like "(((((((" that contains no digits at all.
    private val phoneCharsetRegex = Regex("^\\+?[0-9\\s\\-().]+$")

    private fun isValidPhoneNumber(input: String): Boolean {
        if (!phoneCharsetRegex.matches(input)) return false
        return input.count { it.isDigit() } in 3..15
    }

    override val name = "send_message"
    override val description = "Send an SMS message to a phone number"
    override val parameters = listOf(
        ToolParameter("to", "Recipient phone number", ParameterType.STRING, required = true),
        ToolParameter("body", "Message text", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val to = params["to"]?.toString()
            ?: return ToolResult.error("to is required")
        val body = params["body"]?.toString()
            ?: return ToolResult.error("body is required")

        if (!isValidPhoneNumber(to)) {
            return ToolResult.error("Invalid phone number format: $to")
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            // Always split via divideMessage rather than gating on body.length > 160: that
            // threshold assumes GSM-7 encoding. Any non-GSM-7 text (emoji, Cyrillic, CJK, etc.)
            // between 71 and 160 chars would go through sendTextMessage's single-part path,
            // which fails or truncates at the UCS-2 limit (70 chars) while this tool still
            // reported success. divideMessage computes the correct split for whichever
            // encoding the body actually needs, including a single "part" for short messages.
            val parts = smsManager.divideMessage(body)
            smsManager.sendMultipartTextMessage(to, null, parts, null, null)
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
