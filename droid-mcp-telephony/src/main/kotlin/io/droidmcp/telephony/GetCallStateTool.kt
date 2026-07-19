package io.droidmcp.telephony

import android.content.Context
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Returns the device call state from [android.telephony.TelephonyManager]. The deprecated
 * `callState` getter needs `READ_PHONE_STATE`: without it, apps targeting API &lt; 31 silently
 * get `CALL_STATE_IDLE` (so this tool may falsely report `"idle"`), while apps targeting API 31+
 * get a `SecurityException` instead — caught here and surfaced as a clear error rather than a
 * generic failure. Output: `state` (`"idle"` | `"ringing"` | `"active"` | `"unknown"`).
 */
class GetCallStateTool(private val context: Context) : McpTool {

    override val name = "get_call_state"
    override val description = "Get the current call state of the device"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val callState = try {
            telephonyManager.callState
        } catch (e: SecurityException) {
            return ToolResult.error("READ_PHONE_STATE permission not granted")
        }

        val state = when (callState) {
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
