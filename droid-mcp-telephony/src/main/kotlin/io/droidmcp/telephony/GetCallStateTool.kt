package io.droidmcp.telephony

import android.content.Context
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult

class GetCallStateTool(private val context: Context) : McpTool {

    override val name = "get_call_state"
    override val description = "Get the current call state of the device"
    override val parameters = emptyList<io.droidmcp.core.ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val state = when (telephonyManager.callState) {
            TelephonyManager.CALL_STATE_IDLE -> "idle"
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "active"
            else -> "unknown"
        }

        return ToolResult.success(mapOf(
            "state" to state
        ))
    }
}
