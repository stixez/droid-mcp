package io.droidmcp.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Returns SIM card details from [android.telephony.TelephonyManager]. `simSerialNumber` (ICCID)
 * needs `READ_PHONE_STATE`/privileged access and is null on API 29+ for non-privileged apps;
 * `SecurityException` is caught and yields null. Carrier name and country ISO need no
 * permission. Output: `sim_serial` (nullable), `carrier_name`, `country_iso`, and `slot_index`
 * — which actually carries the default `subscriptionId` (API 23+), NOT the physical SIM slot,
 * and is 0 below API 23 or on error.
 */
class GetSimInfoTool(private val context: Context) : McpTool {

    override val name = "get_sim_info"
    override val description = "Get SIM card information including serial number (ICCID), carrier name, and country ISO"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

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
