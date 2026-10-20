package io.droidmcp.device

import android.os.Environment
import android.os.StatFs
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.util.Locale

/**
 * Reports internal data-partition storage via [StatFs] on the data directory: `total_bytes`,
 * `available_bytes`, `used_bytes`, plus human-readable `total_gb` / `available_gb` strings.
 * No permissions.
 */
class GetStorageInfoTool : McpTool {

    override val name = "get_storage_info"
    override val description = "Get the device's total, free and used internal storage."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        integer("total_bytes")
        integer("available_bytes")
        integer("used_bytes")
        string("total_gb", "Formatted to one decimal, e.g. \"118.2\"")
        string("available_gb")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes

        return ToolResult.success(mapOf(
            "total_bytes" to totalBytes,
            "available_bytes" to availableBytes,
            "used_bytes" to usedBytes,
            // Locale.US, not the default locale — a comma-decimal locale (e.g. "117,2") would
            // otherwise produce a string downstream JSON consumers could misparse as a number.
            "total_gb" to String.format(Locale.US, "%.1f", totalBytes / 1_073_741_824.0),
            "available_gb" to String.format(Locale.US, "%.1f", availableBytes / 1_073_741_824.0),
        ))
    }
}
