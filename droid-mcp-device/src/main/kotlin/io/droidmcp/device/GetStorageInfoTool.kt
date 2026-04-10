package io.droidmcp.device

import android.os.Environment
import android.os.StatFs
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetStorageInfoTool : McpTool {

    override val name = "get_storage_info"
    override val description = "Get device storage information: total, available, and used space in bytes"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        return ToolResult.success(mapOf(
            "total_bytes" to totalBytes,
            "available_bytes" to availableBytes,
            "used_bytes" to usedBytes,
            "total_gb" to String.format("%.1f", totalBytes / 1_073_741_824.0),
            "available_gb" to String.format("%.1f", availableBytes / 1_073_741_824.0),
        ))
    }
}
