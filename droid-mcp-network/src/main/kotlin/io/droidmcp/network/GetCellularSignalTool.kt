package io.droidmcp.network

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import io.droidmcp.tools.McpTool
import io.droidmcp.tools.ToolParameter
import io.droidmcp.tools.ToolResult
import io.droidmcp.util.ParameterType
import io.droidmcp.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetCellularSignalTool(private val context: Context) : McpTool {
    override val name = "get_cellular_signal"
    override val description = "Get current cellular signal strength information including ASU, dBm, and signal level (excellent/good/moderate/poor/none)."
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermissions(context, listOf(Manifest.permission.ACCESS_NETWORK_STATE))) {
            return@withContext ToolResult.error("ACCESS_NETWORK_STATE permission not granted")
        }

        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return@withContext ToolResult.error("TelephonyManager not available")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signalStrength = telephonyManager.signalStrength
                    ?: return@withContext ToolResult.error("Signal strength not available")

                val signalLevels = signalStrength.cellSignalStrengths
                val cellularSignal = signalLevels.firstOrNull { it is android.telephony.CellSignalStrengthGsm ||
                        it is android.telephony.CellSignalStrengthLte ||
                        it is android.telephony.CellSignalStrengthNr ||
                        it is android.telephony.CellSignalStrengthCdma ||
                        it is android.telephony.CellSignalStrengthTdscdma ||
                        it is android.telephony.CellSignalStrengthWcdma }

                if (cellularSignal != null) {
                    val asu = cellularSignal.asuLevel
                    val dbm = cellularSignal.dbm
                    val level = when (cellularSignal.level) {
                        4 -> "excellent"
                        3 -> "good"
                        2 -> "moderate"
                        1 -> "poor"
                        else -> "none"
                    }

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
                @Suppress("DEPRECATION")
                val asu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Use reflection for older APIs
                    try {
                        val method = TelephonyManager::class.java.getMethod("getSignalStrength")
                        val signalStrength = method.invoke(telephonyManager) as? android.telephony.SignalStrength
                        signalStrength?.let {
                            val gsmMethod = it.javaClass.getMethod("getGsmSignalStrength")
                            val gsmStrength = gsmMethod.invoke(it) as? Int ?: 0
                            if (gsmStrength == 99) 0 else gsmStrength
                        } ?: 0
                    } catch (e: Exception) {
                        0
                    }
                } else {
                    0
                }

                @Suppress("DEPRECATION")
                val dbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    -113 + (asu * 2)
                } else {
                    -113 + (asu * 2)
                }

                val level = when {
                    asu >= 20 -> "excellent"
                    asu >= 14 -> "good"
                    asu >= 8 -> "moderate"
                    asu >= 3 -> "poor"
                    else -> "none"
                }

                ToolResult.success(mapOf(
                    "signal_asu" to asu,
                    "signal_dbm" to dbm,
                    "level" to level,
                    "level_numeric" to when {
                        asu >= 20 -> 4
                        asu >= 14 -> 3
                        asu >= 8 -> 2
                        asu >= 3 -> 1
                        else -> 0
                    }
                ))
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Failed to get signal strength: ${e.message}")
        }
    }
}
