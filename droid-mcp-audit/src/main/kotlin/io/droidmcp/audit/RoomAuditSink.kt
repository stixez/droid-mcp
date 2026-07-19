package io.droidmcp.audit

import android.content.Context
import androidx.room.Room
import io.droidmcp.core.AuditSink
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

/**
 * Room-backed [AuditSink] persisting every HTTP `tools/call` to a private
 * on-device database.
 *
 * Wire it into the server with `DroidMcp.Builder.withAuditSink(...)`. Writes are
 * fire-and-forget on a background scope, so [record] returns immediately and a
 * DB hiccup can never fail a tool call. Each write also prunes rows older than
 * [retention].
 *
 * **Privacy note:** the persisted [ToolCallAudit.argumentsJson] contains
 * whatever the LLM passed — message text, contact names, file paths,
 * coordinates. The database lives in the host app's private storage; the host
 * owns its retention, export, and deletion. Set [retention] to
 * [Duration.ZERO] to keep rows indefinitely (pruning is then skipped).
 */
class RoomAuditSink(
    context: Context,
    private val retention: Duration = Duration.ofDays(7),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AuditSink {

    private val db: AuditDatabase = Room.databaseBuilder(
        context.applicationContext,
        AuditDatabase::class.java,
        DB_NAME,
    ).build()

    private val dao: AuditDao = db.auditDao()

    override fun record(entry: ToolCallAudit) {
        scope.launch {
            try {
                dao.insert(entry.toEntity())
                if (!retention.isZero && !retention.isNegative) {
                    dao.deleteOlderThan(System.currentTimeMillis() - retention.toMillis())
                }
            } catch (_: Exception) {
                // A DB failure must never propagate to the request path.
            }
        }
    }

    /** Most recent [limit] calls, newest first. */
    suspend fun recent(limit: Int = 100): List<ToolCallAudit> =
        dao.recent(limit).map { it.toAudit() }

    /** Reactive newest-first stream for a browse UI. */
    fun observe(limit: Int = 100): Flow<List<ToolCallAudit>> =
        dao.observe(limit).map { rows -> rows.map { it.toAudit() } }

    suspend fun count(): Int = dao.count()

    /** Force a retention sweep now. @return rows deleted. No-op if retention is zero. */
    suspend fun pruneNow(): Int =
        if (retention.isZero || retention.isNegative) 0
        else dao.deleteOlderThan(System.currentTimeMillis() - retention.toMillis())

    /** Delete the entire audit history. */
    suspend fun clear() = dao.clear()

    /** Serialize the full history (oldest-first) to a JSON array string. */
    suspend fun exportJson(): String {
        val array = JSONArray()
        dao.all().forEach { row ->
            array.put(
                JSONObject().apply {
                    put("timestamp", row.timestamp)
                    put("tool_name", row.toolName)
                    put("client_label", row.clientLabel ?: JSONObject.NULL)
                    put("arguments_json", row.argumentsJson ?: JSONObject.NULL)
                    put("success", row.success)
                    put("error_message", row.errorMessage ?: JSONObject.NULL)
                    put("duration_ms", row.durationMs)
                }
            )
        }
        return array.toString()
    }

    /** Cancel the write scope and close the database. */
    fun close() {
        scope.cancel()
        db.close()
    }

    companion object {
        const val DB_NAME: String = "droid_mcp_audit.db"
    }
}
