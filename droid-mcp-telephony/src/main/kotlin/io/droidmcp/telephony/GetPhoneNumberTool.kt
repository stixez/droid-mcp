package io.droidmcp.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult

class GetPhoneNumberTool(private val context: Context) : McpTool {

    override val name = "get_phone_number"
    override val description = "Get the phone number of the device"
    override val parameters = emptyList<io.droidmcp.core.ToolParameter>()

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
