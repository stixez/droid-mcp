package io.droidmcp.print

import android.content.Context
import android.content.pm.PackageManager
import android.print.PrintManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ListPrintersTool(private val context: Context) : McpTool {

    override val name = "list_printers"
    override val description = "List installed print service plugins and whether printing is available"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return ToolResult.error("PrintManager not available")

        // Query installed print service packages
        val printServices = context.packageManager
            .queryIntentServices(
                android.content.Intent("android.printservice.PrintService"),
                PackageManager.GET_META_DATA,
            )
            .map { resolveInfo ->
                mapOf(
                    "package" to resolveInfo.serviceInfo.packageName,
                    "name" to resolveInfo.loadLabel(context.packageManager).toString(),
                )
            }

        // Get active print jobs
        val printJobs = printManager.printJobs.map { job ->
            mapOf(
                "id" to job.id.toString(),
                "label" to job.info.label,
                "state" to when {
                    job.isStarted -> "started"
                    job.isQueued -> "queued"
                    job.isCompleted -> "completed"
                    job.isFailed -> "failed"
                    job.isCancelled -> "cancelled"
                    job.isBlocked -> "blocked"
                    else -> "unknown"
                },
            )
        }

        return ToolResult.success(mapOf(
            "print_services" to printServices,
            "service_count" to printServices.size,
            "active_jobs" to printJobs,
            "active_job_count" to printJobs.size,
        ))
    }
}
