package io.droidmcp.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Returns SIM card details from [android.telephony.TelephonyManager]. `simSerialNumber` (ICCID)
 * needs `READ_PHONE_STATE`/privileged access and is null on API 29+ for non-privileged apps;
 * `SecurityException` is caught and yields null. Carrier name and country ISO need no
 * permission. Output: `sim_serial` (nullable), `carrier_name`, `country_iso`,
 * `subscription_id` (the default subscription's id — `TelephonyManager.subscriptionId` on API 30+,
 * `SubscriptionManager.getDefaultSubscriptionId()` below; `-1` if none), and `slot_index` — the
 * physical SIM slot of that subscription (`SubscriptionInfo.simSlotIndex`, which needs
 * `READ_PHONE_STATE`, falling back to the permission-free `SubscriptionManager.getSlotIndex` on
 * API 29+; `-1` if unknown).
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
        val subscriptionId = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.subscriptionId
            } else {
                SubscriptionManager.getDefaultSubscriptionId()
            }
        } catch (e: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        val slotIndex = resolveSlotIndex(subscriptionId)

        return ToolResult.success(mapOf(
            "sim_serial" to simSerialNumber,
            "carrier_name" to carrierName,
            "country_iso" to countryIso,
            "slot_index" to slotIndex,
            "subscription_id" to subscriptionId,
        ))
    }

    /** Physical SIM slot for [subscriptionId], or `-1` when it can't be determined. */
    @SuppressLint("MissingPermission")
    private fun resolveSlotIndex(subscriptionId: Int): Int {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return INVALID_SLOT
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val fromInfo = try {
            subscriptionManager?.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex
        } catch (e: SecurityException) {
            null
        }
        if (fromInfo != null) return fromInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                SubscriptionManager.getSlotIndex(subscriptionId)
            } catch (e: Exception) {
                INVALID_SLOT
            }
        } else {
            INVALID_SLOT
        }
    }

    private companion object {
        const val INVALID_SLOT = -1
    }
}
