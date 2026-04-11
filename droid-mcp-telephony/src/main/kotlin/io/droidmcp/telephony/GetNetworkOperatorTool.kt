package io.droidmcp.telephony

import android.content.Context
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult

class GetNetworkOperatorTool(private val context: Context) : McpTool {

    override val name = "get_network_operator"
    override val description = "Get network operator information including name, ID, MCC, and MNC"
    override val parameters = emptyList<io.droidmcp.core.ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val operatorName = telephonyManager.networkOperatorName
        val operatorId = telephonyManager.networkOperator

        var mcc: String? = null
        var mnc: String? = null

        if (operatorId != null && operatorId.length >= 5) {
            mcc = operatorId.substring(0, 3)
            mnc = operatorId.substring(3)
        }

        return ToolResult.success(mapOf(
            "operator_name" to operatorName,
            "operator_id" to operatorId,
            "mcc" to mcc,
            "mnc" to mnc
        ))
    }
}
