package io.droidmcp.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Returns mobile (cellular) data usage over a look-back window. Prefers
 * [android.app.usage.NetworkStatsManager.querySummaryForDevice] which requires the
 * `PACKAGE_USAGE_STATS` special-access grant; on `SecurityException`/failure — or on API < 29,
 * where a null subscriber id yields an empty (all-zero) summary — it falls back to
 * [android.net.TrafficStats] cumulative-since-boot totals (which ignore the `days` window and
 * add a `note`). Param `days` (1-90, default 30). Output: `bytes_rx`, `bytes_tx`, plus
 * `query_period_days` on the windowed path or `note` (and no `query_period_days`) on the
 * since-boot fallback path. Returns
 * [ToolResult.error] only when even TrafficStats is unsupported.
 */
class GetDataUsageTool(private val context: Context) : McpTool {
    override val name = "get_data_usage"
    override val description = "Get mobile (cellular) data usage statistics over a specified time period; falls back to cumulative since-boot totals if usage-access is not granted"
    override val parameters = listOf(
        ToolParameter(
            name = "days",
            description = "Number of days to look back (1-90, default 30)",
            type = ParameterType.INTEGER,
            required = false,
        )
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val days = (params["days"] as? Number)?.toInt()?.coerceIn(1, 90) ?: 30

        try {
            val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: return@withContext getDataUsageFallback()

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

            // Below API 29 a null subscriberId matches no mobile stats, so the summary comes back
            // empty rather than throwing — treat that as "unsupported" and fall back.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && bucket.rxBytes == 0L && bucket.txBytes == 0L) {
                return@withContext getDataUsageFallback()
            }

            ToolResult.success(mapOf(
                "bytes_rx" to bucket.rxBytes,
                "bytes_tx" to bucket.txBytes,
                "query_period_days" to days,
            ))
        } catch (e: SecurityException) {
            getDataUsageFallback()
        } catch (e: Exception) {
            getDataUsageFallback()
        }
    }

    private fun getDataUsageFallback(): ToolResult {
        return try {
            val mobileRx = TrafficStats.getMobileRxBytes()
            val mobileTx = TrafficStats.getMobileTxBytes()

            if (mobileRx == TrafficStats.UNSUPPORTED.toLong() || mobileTx == TrafficStats.UNSUPPORTED.toLong()) {
                ToolResult.error("Data usage statistics not supported on this device")
            } else {
                ToolResult.success(mapOf(
                    "bytes_rx" to mobileRx,
                    "bytes_tx" to mobileTx,
                    "note" to "Using TrafficStats (cumulative since boot; the days window is not applied)",
                ))
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to retrieve data usage: ${e.message}")
        }
    }
}
