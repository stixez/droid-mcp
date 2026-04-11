package io.droidmcp.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetSimInfoTool(private val context: Context) : McpTool {

    override val name = "get_sim_info"
    override val description = "Get SIM card information including serial number, carrier, and country"
    override val parameters = emptyList<ToolParameter>()

    @SuppressLint("HardwareIds")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        @Suppress("DEPRECATION")
        val simSerialNumber = try {
            telephonyManager.simSerialNumber
        } catch (e: SecurityException) {
            null
        }

        val carrierName = telephonyManager.simOperatorName
        val countryIso = telephonyManager.simCountryIso
        val slotIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                @Suppress("DEPRECATION")
                telephonyManager.subscriptionId
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }

        return ToolResult.success(mapOf(
            "sim_serial" to simSerialNumber,
            "carrier_name" to carrierName,
            "country_iso" to countryIso,
            "slot_index" to slotIndex
        ))
    }
}
