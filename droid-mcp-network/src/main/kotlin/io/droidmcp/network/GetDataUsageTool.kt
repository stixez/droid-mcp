package io.droidmcp.network

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import io.droidmcp.tools.McpTool
import io.droidmcp.tools.ToolParameter
import io.droidmcp.tools.ToolResult
import io.droidmcp.util.ParameterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class GetDataUsageTool(private val context: Context) : McpTool {
    override val name = "get_data_usage"
    override val description = "Get mobile data usage statistics over a specified time period. Returns total bytes sent and received, packets transmitted, and the query period in days."
    override val parameters = listOf(
        ToolParameter(
            name = "days",
            description = "Number of days to look back for usage data. Must be between 1 and 90 days.",
            type = ParameterType.INT,
            required = false
        )
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val days = (params["days"] as? Number)?.toInt()?.coerceIn(1, 90) ?: 30

        try {
            val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

            if (networkStatsManager == null || connectivityManager == null) {
                return@withContext getDataUsageFallback(days)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val subscriberId = getSubscriberId(connectivityManager)
                    ?: return@withContext getDataUsageFallback(days)

                val calendar = Calendar.getInstance()
                val endTime = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, -days)
                val startTime = calendar.timeInMillis

                val query = networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    subscriberId,
                    startTime,
                    endTime
                )

                var bytesRx = 0L
                var bytesTx = 0L
                var packetRx = 0L
                var packetTx = 0L

                query?.use { bucket ->
                    val summary = bucket.nextBucket()
                    while (summary != null) {
                        bytesRx += summary.rxBytes
                        bytesTx += summary.txBytes
                        packetRx += summary.rxPackets
                        packetTx += summary.txPackets
                        bucket.getNextSummary(summary)
                    }
                }

                ToolResult.success(mapOf(
                    "bytes_rx" to bytesRx,
                    "bytes_tx" to bytesTx,
                    "packet_rx" to packetRx,
                    "packet_tx" to packetTx,
                    "query_period_days" to days
                ))
            } else {
                getDataUsageFallback(days)
            }
        } catch (e: SecurityException) {
            ToolResult.error("PACKAGE_USAGE_STATS permission not granted. Please enable usage access in settings.")
        } catch (e: Exception) {
            getDataUsageFallback(days)
        }
    }

    private fun getSubscriberId(connectivityManager: ConnectivityManager): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activeNetwork = connectivityManager.activeNetwork ?: return null
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
            networkCapabilities.subscriptionId?.toString()
        } else {
            null
        }
    }

    private fun getDataUsageFallback(days: Int): ToolResult {
        return try {
            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()

            if (mobileRx == TrafficStats.UNSUPPORTED.toLong() || mobileTx == TrafficStats.UNSUPPORTED.toLong()) {
                ToolResult.error("Data usage statistics are not supported on this device.")
            } else {
                ToolResult.success(mapOf(
                    "bytes_rx" to mobileRx,
                    "bytes_tx" to mobileTx,
                    "packet_rx" to 0L,
                    "packet_tx" to 0L,
                    "query_period_days" to days,
                    "note" to "Using TrafficStats (cumulative lifetime data)"
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to retrieve data usage: ${e.message}")
        }
    }
}
