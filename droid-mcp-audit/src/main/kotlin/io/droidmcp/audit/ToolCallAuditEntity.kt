package io.droidmcp.audit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.droidmcp.core.ToolCallAudit

/**
 * Room row for one audited `tools/call`. Mirrors [ToolCallAudit] plus an
 * auto-incrementing primary key and an index on `timestamp` so retention
 * pruning and recent-first browsing don't table-scan.
 */
@Entity(tableName = "tool_call_audit")
data class ToolCallAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp", index = true) val timestamp: Long,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "client_label") val clientLabel: String?,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String?,
    @ColumnInfo(name = "success") val success: Boolean,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

internal fun ToolCallAudit.toEntity(): ToolCallAuditEntity = ToolCallAuditEntity(
    timestamp = timestamp,
    toolName = toolName,
    clientLabel = clientLabel,
    argumentsJson = argumentsJson,
    success = success,
    errorMessage = errorMessage,
    durationMs = durationMs,
)

internal fun ToolCallAuditEntity.toAudit(): ToolCallAudit = ToolCallAudit(
    timestamp = timestamp,
    toolName = toolName,
    clientLabel = clientLabel,
    argumentsJson = argumentsJson,
    success = success,
    errorMessage = errorMessage,
    durationMs = durationMs,
)
