package io.droidmcp.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Returns the device's line-1 phone number via the deprecated `TelephonyManager.line1Number`.
 * Requires `READ_PHONE_STATE` (or `READ_SMS`/`READ_PHONE_NUMBERS`); even so the value is
 * frequently unavailable (carrier/SIM does not populate it) and `SecurityException` is caught
 * and treated as null. Output: `phone_number` (nullable), `is_available`.
 */
class GetPhoneNumberTool(private val context: Context) : McpTool {

    override val name = "get_phone_number"
    override val description = "Get the phone number of the device"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    @SuppressLint("HardwareIds")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        @Suppress("DEPRECATION")
        val phoneNumber = try {
            telephonyManager.line1Number
        } catch (e: SecurityException) {
            null
        }

        return ToolResult.success(mapOf(
            "phone_number" to phoneNumber,
            "is_available" to (phoneNumber != null)
        ))
    }
}
