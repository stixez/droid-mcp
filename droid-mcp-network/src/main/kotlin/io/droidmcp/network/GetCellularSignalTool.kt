package io.droidmcp.network

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.ParameterType
import io.droidmcp.core.PermissionHelper
import io.droidmcp.core.ToolAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Returns current cellular signal strength. Requires `ACCESS_NETWORK_STATE` (checked up
 * front). On API 29+ reads the first cellular entry from `SignalStrength.cellSignalStrengths`
 * (added in Q — calling it on API 28 throws `NoSuchMethodError`). On API 28 it uses the public
 * `TelephonyManager.getSignalStrength()` + `SignalStrength.getLevel()` for the level; `signal_asu`
 * and `signal_dbm` there come from the GSM reading and are null when it is unknown. Output:
 * `signal_asu`, `signal_dbm`, `level` (`excellent`/`good`/`moderate`/`poor`/`none`),
 * `level_numeric` (0-4). Returns [ToolResult.error] when permission is missing, no
 * SignalStrength/cellular entry is available, or on failure.
 */
class GetCellularSignalTool(private val context: Context) : McpTool {
    override val name = "get_cellular_signal"
    override val description = "Get current cellular signal strength information including ASU, dBm, and signal level (excellent/good/moderate/poor/none)."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermissions(context, listOf(Manifest.permission.ACCESS_NETWORK_STATE))) {
            return@withContext ToolResult.error("ACCESS_NETWORK_STATE permission not granted")
        }

        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return@withContext ToolResult.error("TelephonyManager not available")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signalStrength = telephonyManager.signalStrength
                    ?: return@withContext ToolResult.error("Signal strength not available")

                val signalLevels = signalStrength.cellSignalStrengths
                val cellularSignal = signalLevels.firstOrNull { it is CellSignalStrengthGsm ||
                        it is CellSignalStrengthLte ||
                        it is CellSignalStrengthNr ||
                        it is CellSignalStrengthCdma ||
                        it is CellSignalStrengthTdscdma ||
                        it is CellSignalStrengthWcdma }

                if (cellularSignal != null) {
                    val asu = cellularSignal.asuLevel
                    val dbm = cellularSignal.dbm
                    val level = levelName(cellularSignal.level)

                    ToolResult.success(mapOf(
                        "signal_asu" to asu,
                        "signal_dbm" to dbm,
                        "level" to level,
                        "level_numeric" to cellularSignal.level
                    ))
                } else {
                    ToolResult.error("No cellular signal available")
                }
            } else {
                // API 28: TelephonyManager.getSignalStrength() (API 28) and SignalStrength.getLevel()
                // (API 23) are public; per-technology cellSignalStrengths is API 29+. ASU/dBm are
                // only derivable from the GSM reading — null when it is unknown (99).
                val signalStrength = telephonyManager.signalStrength
                    ?: return@withContext ToolResult.error("Signal strength not available")

                val levelNumeric = signalStrength.level.coerceIn(0, 4)
                @Suppress("DEPRECATION")
                val gsmAsu = signalStrength.gsmSignalStrength
                val asu: Int? = gsmAsu.takeIf { it in 0..31 }
                val dbm: Int? = asu?.let { -113 + (it * 2) }

                ToolResult.success(mapOf(
                    "signal_asu" to asu,
                    "signal_dbm" to dbm,
                    "level" to levelName(levelNumeric),
                    "level_numeric" to levelNumeric,
                ))
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Failed to get signal strength: ${e.message}")
        }
    }

    private fun levelName(level: Int): String = when (level) {
        4 -> "excellent"
        3 -> "good"
        2 -> "moderate"
        1 -> "poor"
        else -> "none"
    }
}
