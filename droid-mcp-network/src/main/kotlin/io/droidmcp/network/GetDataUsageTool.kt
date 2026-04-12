package io.droidmcp.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class GetDataUsageTool(private val context: Context) : McpTool {
    override val name = "get_data_usage"
    override val description = "Get mobile data usage statistics over a specified time period"
    override val parameters = listOf(
        ToolParameter(
            name = "days",
            description = "Number of days to look back (1-90, default 30)",
            type = ParameterType.INTEGER,
            required = false,
        )
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val days = (params["days"] as? Number)?.toInt()?.coerceIn(1, 90) ?: 30

        try {
            val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: return@withContext getDataUsageFallback(days)

            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -days)
            val startTime = calendar.timeInMillis

            val bucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime,
            )

            ToolResult.success(mapOf(
                "bytes_rx" to bucket.rxBytes,
                "bytes_tx" to bucket.txBytes,
                "query_period_days" to days,
            ))
        } catch (e: SecurityException) {
            getDataUsageFallback(days)
        } catch (e: Exception) {
            getDataUsageFallback(days)
        }
    }

    private fun getDataUsageFallback(days: Int): ToolResult {
        return try {
            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()

            if (mobileRx == TrafficStats.UNSUPPORTED.toLong() || mobileTx == TrafficStats.UNSUPPORTED.toLong()) {
                ToolResult.error("Data usage statistics not supported on this device")
            } else {
                ToolResult.success(mapOf(
                    "bytes_rx" to mobileRx,
                    "bytes_tx" to mobileTx,
                    "query_period_days" to days,
                    "note" to "Using TrafficStats (cumulative lifetime data)",
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to retrieve data usage: ${e.message}")
        }
    }
}
