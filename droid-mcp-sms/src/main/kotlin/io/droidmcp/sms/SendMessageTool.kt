package io.droidmcp.sms

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import io.droidmcp.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends an SMS to `to` with text `body` via `SmsManager` (version-checked: system service on
 * API 31+, else `getDefault()`). The recipient must match [phoneRegex] or an error is returned;
 * the body is always split with `divideMessage`/`sendMultipartTextMessage`. Requires `SEND_SMS`.
 *
 * Each part carries an immutable sent-`PendingIntent` (unique request code, delivered to a
 * non-exported receiver registered for the duration of the call), and the tool waits up to 15 s
 * for the radio to report a result for every part. Output: `sent` (true only when every part
 * reported `RESULT_OK`), echoed `to`, `body_length`, `parts`, and `status` (`sent` | `failed` |
 * `timeout`); on `failed`/`timeout` an `error` string explains what happened (a timeout means the
 * outcome is unknown, not that the message was dropped). Exceptions thrown by `SmsManager`
 * itself (bad address, missing permission) return a tool error.
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
    override val description = "Send an SMS message to a phone number. Waits up to 15s for the radio to confirm; `sent` is true only when every part was confirmed sent."
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

        @Suppress("DEPRECATION")
        val smsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            return ToolResult.error("Failed to send SMS: ${e.message}")
        } ?: return ToolResult.error("Failed to send SMS: SmsManager unavailable")

        // Always split via divideMessage rather than gating on body.length > 160: that
        // threshold assumes GSM-7 encoding. Any non-GSM-7 text (emoji, Cyrillic, CJK, etc.)
        // between 71 and 160 chars would go through sendTextMessage's single-part path,
        // which fails or truncates at the UCS-2 limit (70 chars) while this tool still
        // reported success. divideMessage computes the correct split for whichever
        // encoding the body actually needs, including a single "part" for short messages.
        val parts = try {
            smsManager.divideMessage(body)
        } catch (e: Exception) {
            return ToolResult.error("Failed to send SMS: ${e.message}")
        }
        if (parts.isEmpty()) return ToolResult.error("body is empty")

        // Per-call action + per-part request codes so concurrent sends never collide on (or
        // overwrite) each other's PendingIntents.
        val action = "$ACTION_SMS_SENT.${UUID.randomUUID()}"
        val resultCodes = IntArray(parts.size) { PENDING }
        val allReported = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val index = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                synchronized(resultCodes) {
                    if (index !in resultCodes.indices || resultCodes[index] != PENDING) return
                    resultCodes[index] = resultCode
                    if (resultCodes.none { it == PENDING }) allReported.complete(Unit)
                }
            }
        }

        registerSentReceiver(receiver, IntentFilter(action))
        try {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (i in parts.indices) {
                val intent = Intent(action)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_PART_INDEX, i)
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        requestCodes.getAndIncrement(),
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                    )
                )
            }

            try {
                smsManager.sendMultipartTextMessage(to, null, parts, sentIntents, null)
            } catch (e: Exception) {
                return ToolResult.error("Failed to send SMS: ${e.message}")
            }

            val confirmed = withTimeoutOrNull(SEND_TIMEOUT_MS) { allReported.await() } != null
            val codes = synchronized(resultCodes) { resultCodes.copyOf() }
            val failed = codes.filter { it != PENDING && it != Activity.RESULT_OK }

            val result = linkedMapOf<String, Any?>(
                "sent" to (confirmed && failed.isEmpty()),
                "to" to to,
                "body_length" to body.length,
                "parts" to parts.size,
            )
            when {
                failed.isNotEmpty() -> {
                    result["status"] = "failed"
                    result["error"] = "SMS send failed: " +
                        failed.distinct().joinToString(", ") { describeResultCode(it) }
                }
                !confirmed -> {
                    result["status"] = "timeout"
                    result["error"] = "No send confirmation from the radio within ${SEND_TIMEOUT_MS / 1000}s " +
                        "(${codes.count { it == Activity.RESULT_OK }}/${parts.size} parts confirmed); " +
                        "the message may or may not have been sent"
                }
                else -> result["status"] = "sent"
            }
            return ToolResult.success(result)
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
    }

    /** Registers [receiver] non-exported: the sent broadcasts come from our own PendingIntents. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerSentReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun describeResultCode(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic failure"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off"
        SmsManager.RESULT_ERROR_NULL_PDU -> "null PDU"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no service"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "sending limit exceeded"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "short code not allowed"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "short code never allowed"
        else -> "error code $code"
    }

    private companion object {
        const val ACTION_SMS_SENT = "io.droidmcp.sms.SMS_SENT"
        const val EXTRA_PART_INDEX = "io.droidmcp.sms.PART_INDEX"
        const val PENDING = Int.MIN_VALUE
        const val SEND_TIMEOUT_MS = 15_000L
        val requestCodes = AtomicInteger(0x5AC0_0000)
    }
}
